package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hebi.homecrew.data.cloud.HomeCrewLocalMigrationJournal
import com.hebi.homecrew.data.cloud.PendingLocalMigration
import com.hebi.homecrew.data.cloud.RemoteHouseholdChoreGateway
import com.hebi.homecrew.data.cloud.atomicMigrationFits
import com.hebi.homecrew.data.cloud.planStagedMigrationUpload
import com.hebi.homecrew.data.cloud.remainingUploadChunks
import com.hebi.homecrew.data.cloud.readLocalMigrationSource
import com.hebi.homecrew.data.cloud.LocalMigrationPlan
import com.hebi.homecrew.data.cloud.LocalMigrationSource
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import com.hebi.homecrew.data.cloud.migrationMemberNames
import com.hebi.homecrew.data.cloud.prepareLocalMigration
import com.hebi.homecrew.data.local.HomeCrewLocalBackup
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
internal fun HomeCrewLocalMigrationPreviewPanel(
    gateway: RemoteHouseholdChoreGateway,
    journal: HomeCrewLocalMigrationJournal,
    householdId: String,
    accountUid: String,
    snapshot: RemoteSnapshot,
    enabled: Boolean,
    onBusyChange: (Boolean) -> Unit
) {
    if (snapshot.mayBeStale || snapshot.members.none { it.uid == accountUid && it.isOwner }) return
    val context = LocalContext.current.applicationContext
    val palette = LocalHomeCrewColors.current
    val scope = rememberCoroutineScope()
    var source by remember(householdId, accountUid) { mutableStateOf<LocalMigrationSource?>(null) }
    var mapping by remember(householdId, accountUid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var plan by remember(householdId, accountUid, snapshot.members, snapshot.chores, snapshot.occurrences) {
        mutableStateOf<LocalMigrationPlan?>(null)
    }
    var digest by remember(householdId, accountUid, snapshot.members, snapshot.chores, snapshot.occurrences) {
        mutableStateOf<String?>(null)
    }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember(journal) { mutableStateOf<PendingLocalMigration?>(null) }
    var journalReady by remember(journal) { mutableStateOf(false) }
    var journalFailed by remember(journal) { mutableStateOf(false) }
    var journalReload by remember(journal) { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestEnabled by rememberUpdatedState(enabled)
    val busyCallback by rememberUpdatedState(onBusyChange)
    DisposableEffect(journal) { onDispose { busyCallback(false) } }
    LaunchedEffect(journal, journalReload) {
        journalReady = false
        journalFailed = false
        try {
            pending = journal.load()
            journalReady = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            journalFailed = true
        }
    }

    fun transfer(retry: PendingLocalMigration? = null) {
        if (!journalReady || journalFailed || loading || !enabled) return
        val expectedDigest = retry?.digest ?: digest ?: return
        val selectedMapping = retry?.mapping ?: mapping.toMap()
        val verified = snapshot
        val previousSource = retry?.household ?: source?.household ?: return
        loading = true
        busyCallback(true)
        message = null
        scope.launch {
            try {
                // Re-check the saved receipt first. Never re-upload merely to
                // determine whether a previous attempt was committed.
                if (retry != null) {
                    val receipt = withTimeout(35_000) { gateway.migrationReceipt(householdId, expectedDigest) }
                    if (receipt != null) {
                        pending = journal.confirm(receipt)
                        message = "서버 이전 영수증과 보존한 자료가 일치합니다."
                        return@launch
                    }
                }
                val fresh = readLocalMigrationSource(context)
                val (manifest, backup) = withContext(Dispatchers.Default) {
                    check(fresh.household == previousSource) { "Local household changed" }
                    val candidate = checkNotNull(prepareLocalMigration(fresh, householdId, accountUid,
                        verified, selectedMapping).manifest)
                    check(candidate.digest == expectedDigest) { "Local migration preview changed" }
                    if (!atomicMigrationFits(candidate)) planStagedMigrationUpload(candidate)
                    val backup = HomeCrewLocalBackup.encode(fresh.household, fresh.rows)
                    val restored = HomeCrewLocalBackup.decode(backup, fresh.household)
                    check(restored == fresh.rows) { "Backup verification failed" }
                    val record = PendingLocalMigration(expectedDigest, fresh.household, selectedMapping, backup,
                        fresh.rows.definitions.size, fresh.rows.occurrences.size,
                        fresh.rows.occurrences.count { it.completed },
                        fresh.rows.occurrences.count { it.skippedAtEpochMillis != null })
                    if (retry != null) check(record == retry) { "Original backup changed" }
                    candidate to record
                }
                journal.stage(backup)
                pending = backup
                // The callable also verifies owner, membership, empty target
                // and manifest digest atomically immediately before writing.
                check(latestEnabled && !latestSnapshot.mayBeStale &&
                    latestSnapshot.members == verified.members &&
                    latestSnapshot.chores.isEmpty() && latestSnapshot.occurrences.isEmpty()) {
                    "Target changed"
                }
                if (withContext(Dispatchers.Default) { atomicMigrationFits(manifest) }) {
                    val receipt = withTimeout(65_000) { gateway.importLocalManifest(manifest) }
                    pending = journal.confirm(receipt)
                    message = "서버 이전 영수증과 보존한 자료가 일치합니다. 로컬 원본은 유지했습니다."
                } else {
                    val upload = withContext(Dispatchers.Default) { planStagedMigrationUpload(manifest) }
                    var stage = withTimeout(35_000) { gateway.beginMigrationStage(upload) }
                    val missing = withContext(Dispatchers.Default) { remainingUploadChunks(upload, stage).take(8) }
                    for (chunk in missing) {
                        check(latestEnabled && !latestSnapshot.mayBeStale &&
                            latestSnapshot.members == verified.members)
                        stage = withTimeout(35_000) { gateway.appendMigrationChunk(householdId, stage.sessionId, chunk) }
                        check(stage.digest == expectedDigest && stage.nextIndex > chunk.index)
                        message = "분할 저장 ${stage.nextIndex}/${stage.chunkCount}개"
                    }
                    message = if (stage.nextIndex == stage.chunkCount) {
                        "분할 저장 완료. 공동 공간 관리의 대용량 이전 관리에서 검증 및 최종 반영을 진행해 주세요."
                    } else {
                        "분할 저장 ${stage.nextIndex}/${stage.chunkCount}개. 보존한 동일 요청으로 이어서 전송해 주세요."
                    }
                }
                plan = null
            } catch (_: TimeoutCancellationException) {
                message = "서버 응답 시간이 초과되었습니다. 원본과 보관된 요청을 유지했습니다. 이전 결과를 확인해 주세요."
                journalReload++
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "이전 결과를 확정할 수 없습니다. 원본과 보관된 요청을 유지했습니다. 서버 상태를 다시 확인해 주세요."
                journalReload++
            } finally {
                loading = false
                busyCallback(false)
            }
        }
    }

    fun checkReceipt(saved: PendingLocalMigration) {
        if (!journalReady || journalFailed || loading || !enabled) return
        loading = true
        busyCallback(true)
        scope.launch {
            try {
                val receipt = withTimeout(35_000) { gateway.migrationReceipt(householdId, saved.digest) }
                if (receipt != null) {
                    pending = journal.confirm(receipt)
                    message = "서버 이전 영수증을 확인했습니다. 원본 백업은 기기에 보존됩니다."
                } else {
                    message = "서버에서 완료 영수증을 찾지 못했습니다. 진행 중인 요청이 있을 수 있습니다."
                }
            } catch (_: TimeoutCancellationException) {
                message = "서버 영수증 확인 시간이 초과되었습니다. 보관된 요청은 유지됩니다."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "서버 영수증을 확인하지 못했습니다."
            } finally { loading = false; busyCallback(false) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
        Text("로컬 자료 이전 준비", style = MaterialTheme.typography.titleMedium)
        Text("명시적으로 확인한 경우에만 로컬 집안일을 공동 공간에 복사합니다. 원본은 삭제하지 않습니다.",
            style = MaterialTheme.typography.bodySmall, color = palette.muted)
        if (journalFailed) {
            Text("보관된 이전 자료를 읽을 수 없습니다. 새 이전을 차단했습니다.", color = palette.error)
            TextButton(enabled = !loading, onClick = { journalReload++ }) { Text("보관 상태 다시 확인") }
        } else if (!journalReady) Text("이전 요청 보관 상태 확인 중…")
        pending?.let { saved ->
            Text(if (saved.confirmed) "서버 이전 영수증 확인 완료" else "기기에 보존된 이전 요청",
                color = if (saved.confirmed) palette.success else palette.warning)
            Text("집안일 ${saved.definitions}개 · 회차 ${saved.occurrences}개 · 완료 ${saved.completed}개 · 건너뜀 ${saved.skipped}개")
            Text("원본 백업은 앱 내부에 유지됩니다. 앱 삭제 전에는 기존 백업 기능으로 별도 보관해 주세요.",
                style = MaterialTheme.typography.bodySmall, color = palette.muted)
            if (!saved.confirmed) {
                Button(enabled = enabled && journalReady && !journalFailed && !loading,
                    onClick = { checkReceipt(saved) }, modifier = Modifier.fillMaxWidth()) {
                    Text("서버 이전 결과 확인")
                }
                TextButton(
                    enabled = enabled && journalReady && !journalFailed && !loading &&
                        snapshot.chores.isEmpty() && snapshot.occurrences.isEmpty(),
                    onClick = { confirm = true }
                ) { Text("보존한 동일 요청 재시도") }
            }
        }
        Button(
            enabled = enabled && journalReady && !journalFailed && pending == null && !loading &&
                snapshot.chores.isEmpty() && snapshot.occurrences.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                loading = true
                source = null
                plan = null
                digest = null
                mapping = emptyMap()
                message = null
                scope.launch {
                    try {
                        source = readLocalMigrationSource(context)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        message = "로컬 자료를 확인하지 못했습니다. 다시 시도해 주세요."
                    } finally { loading = false }
                }
            }
        ) { Text(if (loading) "확인 중…" else "로컬 자료 읽기") }
        if (snapshot.chores.isNotEmpty() || snapshot.occurrences.isNotEmpty()) {
            Text("기존 공동 집안일이 있는 공간은 이전 대상에서 제외합니다.",
                style = MaterialTheme.typography.bodySmall, color = palette.warning)
        }
        source?.let { local ->
            Text("집안일 ${local.rows.definitions.size}개 · 회차 ${local.rows.occurrences.size}개")
            Text("로컬 이름마다 실제 계정을 직접 선택해 주세요.", style = MaterialTheme.typography.bodySmall)
            val requiredNames = remember(local) { migrationMemberNames(local) }
            if (requiredNames.size > 8) {
                Text("로컬 이름이 ${requiredNames.size}개입니다. 공동 공간은 최대 8명까지 연결할 수 있어 이전 준비를 중단합니다.",
                    color = palette.warning, style = MaterialTheme.typography.bodySmall)
            }
            requiredNames.takeIf { it.size <= 8 }.orEmpty().forEach { name ->
                Text(name, style = MaterialTheme.typography.bodyMedium)
                snapshot.members.forEachIndexed { index, member ->
                    val label = if (member.isOwner) "소유자" else "구성원 $index"
                    val selected = mapping[name] == member.uid
                    val occupied = mapping.any { it.key != name && it.value == member.uid }
                    TextButton(
                        enabled = enabled && journalReady && pending == null && !loading && !occupied,
                        onClick = {
                            mapping = if (selected) mapping - name else mapping + (name to member.uid)
                            plan = null
                            digest = null
                        }
                    ) { Text(label + (if (member.uid == accountUid) " (나)" else "") + if (selected) " · 선택" else "") }
                }
            }
            Button(
                enabled = enabled && journalReady && pending == null && !loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    loading = true
                    plan = null
                    digest = null
                    val selectedMapping = mapping.toMap()
                    val verified = snapshot
                    scope.launch {
                        try {
                            val (prepared, fingerprint) = withContext(Dispatchers.Default) {
                                val result = prepareLocalMigration(local, householdId, accountUid, verified, selectedMapping)
                                result to result.manifest?.digest
                            }
                            digest = fingerprint
                            plan = prepared
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            message = "이전 준비를 검증하지 못했습니다."
                        } finally { loading = false }
                    }
                }
            ) { Text("이전 대상 검사") }
        }
        plan?.let { result ->
            Text("완료 ${result.completedCount}개 · 건너뜀 ${result.skippedCount}개")
            result.blockers.forEach { Text(it, color = palette.warning, style = MaterialTheme.typography.bodySmall) }
            if (result.manifest != null && pending == null) {
                val fits = remember(result.manifest) { atomicMigrationFits(result.manifest) }
                Text("이전용 자료 구성 완료 · 아직 업로드하지 않았습니다.", color = palette.success)
                Text("원본 업무량이 0인 회차 ${result.manifest.legacyWorkloadCount}개는 원래 값과 원본 집안일 점수를 함께 보존합니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                digest?.let { Text("검증 식별자: " + it.take(12), style = MaterialTheme.typography.bodySmall) }
                if (!fits) Text("대용량 자료는 비공개로 분할 저장한 뒤 소유자가 별도 확인해야 공개됩니다. 서버 한도와 안전 여유를 초과하면 전송하지 않습니다.", color = palette.muted)
                Button(enabled = enabled && journalReady && !journalFailed && !loading && digest != null,
                    onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (fits) "원본 보존 후 공동 공간으로 복사" else "원본 보존 후 분할 저장")
                }
            }
        }
        if (loading) Text("이전 자료 또는 서버 응답을 확인하고 있습니다…")
        message?.let { Text(it, color = palette.warning, style = MaterialTheme.typography.bodySmall) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("공동 공간에 복사할까요?") },
            text = { Text("기기에 원본 백업을 먼저 저장합니다. 소규모 자료는 공동 공간으로 복사하고, 대용량 자료는 비공개 분할 저장합니다. 최종 공유는 별도 확인이 필요합니다. 원본이나 대상이 변경되면 이전을 중단합니다.") },
            confirmButton = { TextButton(onClick = {
                confirm = false
                transfer(pending)
            }) { Text("확인 후 복사") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } }
        )
    }
}
