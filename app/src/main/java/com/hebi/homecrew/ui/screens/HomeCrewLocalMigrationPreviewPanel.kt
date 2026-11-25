package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hebi.homecrew.data.cloud.LocalMigrationPlan
import com.hebi.homecrew.data.cloud.LocalMigrationSource
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import com.hebi.homecrew.data.cloud.migrationMemberNames
import com.hebi.homecrew.data.cloud.prepareLocalMigration
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HomeCrewLocalMigrationPreviewPanel(
    householdId: String,
    accountUid: String,
    snapshot: RemoteSnapshot,
    enabled: Boolean
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

    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
        Text("로컬 자료 이전 준비", style = MaterialTheme.typography.titleMedium)
        Text("이 단계는 기기 자료를 읽고 이전 대상을 검토합니다. 서버에 전송하거나 원본을 변경하지 않습니다.",
            style = MaterialTheme.typography.bodySmall, color = palette.muted)
        Button(
            enabled = enabled && !loading && snapshot.chores.isEmpty() && snapshot.occurrences.isEmpty(),
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
                        source = withContext(Dispatchers.IO) {
                            val store = LocalHouseholdStore(context)
                            val before = checkNotNull(store.load())
                            val rows = HomeCrewDatabase.get(context).chores().readRows()
                            check(before == store.load()) { "Local setup changed" }
                            LocalMigrationSource(before, rows)
                        }
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
                        enabled = enabled && !loading && !occupied,
                        onClick = {
                            mapping = if (selected) mapping - name else mapping + (name to member.uid)
                            plan = null
                            digest = null
                        }
                    ) { Text(label + (if (member.uid == accountUid) " (나)" else "") + if (selected) " · 선택" else "") }
                }
            }
            Button(
                enabled = enabled && !loading,
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
            if (result.manifest != null) {
                Text("이전용 자료 구성 완료 · 아직 업로드하지 않았습니다.", color = palette.success)
                Text("원본 업무량이 0인 회차 ${result.manifest.legacyWorkloadCount}개는 원래 값과 원본 집안일 점수를 함께 보존합니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                digest?.let { Text("검증 식별자: " + it.take(12), style = MaterialTheme.typography.bodySmall) }
            }
        }
        message?.let { Text(it, color = palette.warning, style = MaterialTheme.typography.bodySmall) }
    }
}
