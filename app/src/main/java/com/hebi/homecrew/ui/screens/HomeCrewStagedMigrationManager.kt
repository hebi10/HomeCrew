package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.HomeCrewLocalMigrationJournal
import com.hebi.homecrew.data.cloud.RemoteHouseholdChoreGateway
import com.hebi.homecrew.data.cloud.RemoteMigrationStage
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class MigrationStageAction(val label: String, val explanation: String) {
    SEAL("전체 검증", "분할 저장한 전체 자료의 무결성과 현재 권한을 서버에서 검증합니다. 아직 공개하지 않습니다."),
    PUBLISH("최종 반영 또는 이어서 반영", "검증된 자료를 공동 공간으로 반영합니다. 마지막 청크까지 완료될 때까지 조회와 수정이 잠깁니다."),
    ROLLBACK("숨겨진 반영분 되돌리기", "아직 공개하지 않은 이 이전 세션의 반영분만 되돌립니다. 이미 공개한 자료나 로컬 원본은 삭제하지 않습니다."),
    CANCEL("분할 저장 취소", "현재 비공개 임시 청크를 정리합니다. 로컬 백업은 그대로 유지됩니다."),
    CLEANUP("완료한 임시 자료 정리", "공개된 공동 집안일과 완료 영수증은 유지하고 비공개 임시 청크만 정리합니다.")
}

@Composable
internal fun HomeCrewStagedMigrationManager(
    gateway: RemoteHouseholdChoreGateway,
    journal: HomeCrewLocalMigrationJournal,
    householdId: String,
    householdName: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stage by remember(householdId) { mutableStateOf<RemoteMigrationStage?>(null) }
    var loaded by remember(householdId) { mutableStateOf(false) }
    var busy by remember(householdId) { mutableStateOf(false) }
    var message by remember(householdId) { mutableStateOf<String?>(null) }
    var confirm by remember(householdId) { mutableStateOf<MigrationStageAction?>(null) }

    suspend fun refresh() {
        loaded = false
        stage = withTimeout(35_000) { gateway.migrationStage(householdId) }
        loaded = true
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { action() }
            catch (_: TimeoutCancellationException) {
                loaded = false
                message = "응답 시간이 초과되었습니다. 서버 상태를 다시 확인해 주세요."
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                loaded = false
                message = "요청 결과를 확인하지 못했습니다. 현재 권한과 서버 상태를 다시 확인해 주세요."
            } finally { busy = false }
        }
    }

    LaunchedEffect(householdId, gateway) { run { refresh() } }

    fun execute(action: MigrationStageAction, selected: RemoteMigrationStage) {
        run {
            val fresh = withTimeout(35_000) { gateway.migrationStage(householdId) }
            check(fresh == selected) { "Stage changed" }
            when (action) {
                MigrationStageAction.SEAL -> {
                    stage = withTimeout(195_000) { gateway.sealMigrationStage(householdId, selected.sessionId) }
                    message = "서버 전체 검증을 확인했습니다. 최종 반영은 별도 확인이 필요합니다."
                }
                MigrationStageAction.PUBLISH -> {
                    var current = selected
                    repeat(8) {
                        if (current.state == "SEALED" || current.state == "PUBLISHING") {
                            withTimeout(65_000) { gateway.publishMigrationChunk(householdId, current) }
                            val next = checkNotNull(withTimeout(35_000) { gateway.migrationStage(householdId) })
                            check(next.sessionId == current.sessionId && next.digest == current.digest &&
                                next.publishIndex > current.publishIndex)
                            current = next
                            stage = next
                        }
                    }
                    if (current.state == "PUBLISHED") {
                        val receipt = checkNotNull(withTimeout(35_000) { gateway.migrationReceipt(householdId, current.digest) })
                        val local = journal.load()
                        if (local != null && local.digest == receipt.digest) journal.confirm(receipt)
                        message = "서버 완료 영수증 확인: 집안일 ${receipt.definitions}개, 회차 ${receipt.occurrences}개. 임시 자료는 별도로 정리할 수 있습니다."
                    } else message = "반영 ${current.publishIndex}/${current.chunkCount}개. 다음 반영을 이어서 진행해 주세요."
                }
                MigrationStageAction.ROLLBACK -> {
                    withTimeout(65_000) { gateway.rollbackMigrationPublication(householdId, selected.sessionId) }
                    refresh()
                    message = if (stage?.state == "ROLLED_BACK") "부분 반영을 되돌렸습니다. 임시 청크를 취소할 수 있습니다."
                        else "한 청크를 되돌렸습니다. 이어서 되돌려 주세요."
                }
                MigrationStageAction.CANCEL -> {
                    withTimeout(65_000) { gateway.cancelMigrationStage(householdId, selected.sessionId) }
                    refresh()
                    message = if (stage == null) "임시 자료 정리 완료. 로컬 백업은 유지됩니다." else "임시 자료를 일부 정리했습니다. 취소를 이어서 진행해 주세요."
                }
                MigrationStageAction.CLEANUP -> {
                    withTimeout(65_000) { gateway.cleanupMigrationStage(householdId, selected.sessionId) }
                    refresh()
                    message = if (stage == null) "임시 자료 정리 완료. 공개 자료와 영수증은 유지됩니다." else "임시 자료 정리를 이어서 진행해 주세요."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("대용량 이전 관리") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                Text(householdName, style = MaterialTheme.typography.titleMedium)
                Text("공동 집안일 조회와 독립된 소유자 전용 서버 이전 관리입니다. 모든 변경은 직접 확인한 경우에만 실행합니다.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { run { refresh() } }) { Text("서버 상태 확인") }
                if (loaded && stage == null) Text("진행 중인 대용량 이전이 없습니다. 공동 집안일의 로컬 자료 이전 화면에서 분할 저장을 시작해 주세요.")
                if (loaded) stage?.let { current ->
                    val label = when (current.state) {
                        "UPLOADING" -> "비공개 분할 저장"
                        "VERIFYING" -> "전체 검증 중"
                        "SEALED" -> "전체 검증 완료 · 미공개"
                        "PUBLISHING" -> "반영 중 · 조회 잠김"
                        "ROLLING_BACK" -> "부분 반영 복구 중"
                        "ROLLED_BACK" -> "부분 반영 복구 완료"
                        "PUBLISHED" -> "공개 완료"
                        "CLEANING", "CANCELLING" -> "임시 자료 정리 중"
                        else -> "검증 실패"
                    }
                    Text(label)
                    Text("저장 ${current.nextIndex}/${current.chunkCount} 청크 · 집안일 ${current.receivedDefinitions}/${current.definitionCount}개 · 회차 ${current.receivedOccurrences}/${current.occurrenceCount}개")
                    Text("반영 ${current.publishIndex}/${current.chunkCount} 청크")
                    val actions = when (current.state) {
                        "UPLOADING" -> if (current.nextIndex == current.chunkCount) listOf(MigrationStageAction.SEAL, MigrationStageAction.CANCEL)
                            else listOf(MigrationStageAction.CANCEL)
                        "VERIFYING" -> listOf(MigrationStageAction.SEAL, MigrationStageAction.CANCEL)
                        "SEALED" -> listOf(MigrationStageAction.PUBLISH, MigrationStageAction.CANCEL)
                        "PUBLISHING" -> listOf(MigrationStageAction.PUBLISH, MigrationStageAction.ROLLBACK)
                        "ROLLING_BACK" -> listOf(MigrationStageAction.ROLLBACK)
                        "PUBLISHED", "CLEANING" -> listOf(MigrationStageAction.CLEANUP)
                        else -> listOf(MigrationStageAction.CANCEL)
                    }
                    if (current.state == "UPLOADING" && current.nextIndex < current.chunkCount) {
                        Text("공동 집안일 화면에서 보존한 동일 요청을 재시도하여 남은 청크를 저장해 주세요.")
                    }
                    actions.forEach { action ->
                        Button(enabled = !busy, onClick = { confirm = action }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.label)
                        }
                    }
                }
                if (busy) Text("서버 요청 중…")
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text("닫기") } }
    )
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.label) },
            text = { Text(action.explanation) },
            confirmButton = {
                TextButton(onClick = {
                    val selected = stage
                    confirm = null
                    if (loaded && selected != null) execute(action, selected)
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } }
        )
    }
}
