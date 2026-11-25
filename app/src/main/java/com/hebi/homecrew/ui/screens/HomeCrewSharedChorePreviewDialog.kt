package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.HomeCrewCompletionJournal
import com.hebi.homecrew.data.cloud.HomeCrewLocalMigrationJournal
import com.hebi.homecrew.data.cloud.RemoteCompletionCommand
import com.hebi.homecrew.data.cloud.RemoteCompletionResult
import com.hebi.homecrew.data.cloud.RemoteHouseholdChoreGateway
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared-household preview with explicit online completion commands.
 * The local Room screens and records are not modified.
 */
@Composable
internal fun HomeCrewSharedChorePreviewDialog(
    gateway: RemoteHouseholdChoreGateway,
    journal: HomeCrewCompletionJournal,
    migrationJournal: HomeCrewLocalMigrationJournal,
    householdId: String,
    householdName: String,
    accountUid: String,
    onClose: () -> Unit
) {
    key(gateway, journal, migrationJournal, accountUid, householdId) {
        val palette = LocalHomeCrewColors.current
        var day by rememberSaveable { mutableLongStateOf(sharedChoreToday().toEpochDay()) }
        var retry by remember { mutableIntStateOf(0) }
        val date = LocalDate.ofEpochDay(day)
        val scope = rememberCoroutineScope()
        var completion by remember { mutableStateOf<SharedCompletionState>(SharedCompletionState.Idle) }
        var journalReady by remember { mutableStateOf(false) }
        var journalError by remember { mutableStateOf(false) }
        var loadAttempt by remember { mutableIntStateOf(0) }
        var scheduleBusy by remember { mutableStateOf(false) }
        var migrationBusy by remember { mutableStateOf(false) }

        LaunchedEffect(journal, loadAttempt) {
            journalReady = false
            journalError = false
            try {
                val restored = journal.load()
                completion = restored?.let { SharedCompletionState.Uncertain(it) }
                    ?: SharedCompletionState.Idle
                journalReady = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                journalError = true
            }
        }

        key(retry) {
            val stream = remember(gateway, householdId) { observeSharedChorePreview(gateway, householdId) }
            val state by stream.collectAsState(initial = SharedChorePreviewState.Loading)

            LaunchedEffect(state, completion, journalReady, journalError) {
                val pending = completion as? SharedCompletionState.AwaitingSnapshot
                if (journalReady && !journalError && pending != null) {
                    val reconciled = reconcileSharedCompletion(pending, state)
                    if (reconciled != pending) {
                        try {
                            journal.remove(pending.command)
                            completion = reconciled
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            journalError = true
                            journalReady = false
                        }
                    }
                }
            }

            fun send(command: RemoteCompletionCommand) {
                if (!journalReady || journalError || scheduleBusy || migrationBusy ||
                    state !is SharedChorePreviewState.Ready ||
                    completion is SharedCompletionState.Sending
                ) return
                completion = SharedCompletionState.Sending(command)
                scope.launch {
                    try {
                        // Never submit a command that cannot survive process death.
                        journal.stage(command)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        completion = SharedCompletionState.Uncertain(command)
                        journalError = true
                        journalReady = false
                        return@launch
                    }
                    val result = try {
                        // Connectivity may have changed while committing to disk.
                        if (state !is SharedChorePreviewState.Ready) RemoteCompletionResult.Unknown
                        else withTimeoutOrNull(35_000) {
                            gateway.recordCompletion(householdId, command)
                        } ?: RemoteCompletionResult.Unknown
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        RemoteCompletionResult.Unknown
                    }
                    val response = sharedCompletionResponse(command, result)
                    if (response is SharedCompletionState.Notice) {
                        try {
                            journal.remove(command)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            completion = SharedCompletionState.Uncertain(command)
                            journalReady = false
                            journalError = true
                            retry++
                            return@launch
                        }
                    }
                    completion = response
                    if (result !is RemoteCompletionResult.Accepted) retry++
                }
            }

            fun discard(command: RemoteCompletionCommand) {
                if (!journalReady || journalError || state !is SharedChorePreviewState.Ready ||
                    completion is SharedCompletionState.Sending
                ) return
                completion = SharedCompletionState.Sending(command)
                scope.launch {
                    try {
                        journal.remove(command)
                        completion = SharedCompletionState.Notice(SharedCompletionNotice.RECHECK)
                        retry++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        completion = SharedCompletionState.Uncertain(command)
                        journalReady = false
                        journalError = true
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { if (completion !is SharedCompletionState.Sending && !scheduleBusy && !migrationBusy) onClose() },
                title = { Text(if (state is SharedChorePreviewState.Ready) householdName else "공동 집안일") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
                    ) {
                        Text(
                            "공동 집안일 미리보기 · 온라인 완료 관리",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted
                        )
                        Text(
                            "서버에서 확인된 상태만 표시합니다. 전송 전 요청을 기기에 보존하며, 연결 복구 후 직접 재시도할 수 있습니다. 기존 로컬 집안일은 변경하지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted
                        )
                        if (journalError) {
                            Text("기기에 저장된 요청을 확인하지 못했습니다. 새 변경을 중단했습니다.", color = palette.error)
                            TextButton(onClick = { loadAttempt++ }) { Text("저장 상태 다시 확인") }
                        } else if (!journalReady) {
                            Text("보관된 요청을 확인하고 있습니다…")
                        }
                        when (val pending = completion) {
                            SharedCompletionState.Idle -> Unit
                            is SharedCompletionState.Sending -> Text("완료 변경 요청을 전송하고 있습니다…")
                            is SharedCompletionState.AwaitingSnapshot -> {
                                Text("서버 응답을 받았습니다. 최신 집안일 상태를 확인하고 있습니다…")
                                TextButton(onClick = { retry++ }) { Text("상태 다시 확인") }
                                TextButton(
                                    enabled = journalReady && !journalError && state is SharedChorePreviewState.Ready,
                                    onClick = { discard(pending.command) }
                                ) { Text("요청 확인 중단 및 새로고침") }
                            }
                            is SharedCompletionState.Uncertain -> {
                                Text(
                                    "완료 변경 요청의 결과가 미확정입니다. 보관된 요청은 앱을 다시 열어도 복구할 수 있습니다.",
                                    color = palette.warning
                                )
                                TextButton(
                                    enabled = journalReady && !journalError &&
                                        retrySharedCompletionCommand(state, pending) != null,
                                    onClick = { retrySharedCompletionCommand(state, completion)?.let(::send) }
                                ) { Text("동일 요청 다시 시도") }
                                TextButton(
                                    enabled = journalReady && !journalError && state is SharedChorePreviewState.Ready,
                                    onClick = { discard(pending.command) }
                                ) { Text("재시도 중단 및 새로고침") }
                            }
                            is SharedCompletionState.Notice -> {
                                val message = when (pending.type) {
                                    SharedCompletionNotice.CONFIRMED -> "서버에서 변경을 확인했습니다."
                                    SharedCompletionNotice.SUPERSEDED -> "다른 변경이 반영되었습니다. 최신 상태를 확인해 주세요."
                                    SharedCompletionNotice.CONFLICT -> "다른 기기에서 먼저 변경했습니다. 최신 상태를 확인한 후 다시 선택해 주세요."
                                    SharedCompletionNotice.REJECTED -> "변경 권한 또는 집안일 상태를 확인해 주세요. 변경하지 않았습니다."
                                    SharedCompletionNotice.RECHECK -> "재시도를 중단했습니다. 기존 요청이 처리되었을 수 있으니 최신 상태를 확인해 주세요."
                                }
                                Text(message, style = MaterialTheme.typography.bodySmall,
                                    color = if (pending.type == SharedCompletionNotice.CONFIRMED) palette.success else palette.warning)
                            }
                        }
                        when (val current = state) {
                            SharedChorePreviewState.Loading -> Text("서버에서 집안일을 확인하고 있습니다…")
                            SharedChorePreviewState.Offline -> {
                                Text("연결을 확인하고 있습니다. 서버에서 확인되지 않은 집안일은 표시하지 않습니다.")
                                TextButton(onClick = { retry++ }) { Text("다시 연결") }
                            }
                            SharedChorePreviewState.Unavailable -> {
                                Text("공동 공간의 정보를 확인하지 못했습니다. 연결 및 참여 권한을 확인해 주세요.")
                                TextButton(onClick = { retry++ }) { Text("다시 시도") }
                            }
                            is SharedChorePreviewState.Ready -> {
                                Text("실시간 연결", color = palette.success, style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { day = date.minusDays(1).toEpochDay() }) { Text("이전") }
                                    TextButton(onClick = { day = sharedChoreToday().toEpochDay() }) { Text("오늘") }
                                    TextButton(onClick = { day = date.plusDays(1).toEpochDay() }) { Text("다음") }
                                }
                                Text(date.toString() + " (한국 시간)", style = MaterialTheme.typography.titleMedium)
                                val daily = sharedChoreDay(current.snapshot, date, accountUid)
                                if (daily.rows.isEmpty() && daily.missingDefinitionCount == 0) {
                                    Text("이날 예정된 공동 집안일이 없습니다.")
                                }
                                daily.rows.forEach { row ->
                                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                                        val label = when (row.status) {
                                            SharedChoreStatus.PENDING -> "미완료"
                                            SharedChoreStatus.COMPLETED -> "완료"
                                            SharedChoreStatus.SKIPPED -> "건너뜀"
                                            SharedChoreStatus.SCHEDULE_CANCELLED -> "일정 취소"
                                        }
                                        val owner = if (row.assignedToMe) "내 담당" else "다른 구성원 담당"
                                        Text(
                                            listOf(row.category, owner, "난이도 ${row.points}", label)
                                                .filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.muted
                                        )
                                        if (row.status == SharedChoreStatus.PENDING ||
                                            row.status == SharedChoreStatus.COMPLETED) {
                                            TextButton(
                                                enabled = journalReady && !journalError && !scheduleBusy && !migrationBusy &&
                                                    canStartSharedCompletion(state, completion),
                                                modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget),
                                                onClick = {
                                                    newSharedCompletionCommand(state, completion, row.occurrenceId)
                                                        ?.let(::send)
                                                }
                                            ) {
                                                Text(if (row.status == SharedChoreStatus.COMPLETED) "완료 취소" else "완료 처리")
                                            }
                                        }
                                    }
                                }
                                if (daily.missingDefinitionCount > 0) {
                                    Text(
                                        "일부 집안일 정보가 아직 일치하지 않습니다. 동기화 후 다시 확인해 주세요.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.warning
                                    )
                                }
                                HomeCrewSharedSchedulePanel(
                                    gateway = gateway,
                                    householdId = householdId,
                                    accountUid = accountUid,
                                    snapshot = current.snapshot,
                                    enabled = journalReady && !journalError && !migrationBusy &&
                                        canStartSharedCompletion(state, completion),
                                    onBusyChange = { scheduleBusy = it }
                                )
                                HomeCrewLocalMigrationPreviewPanel(
                                    gateway = gateway,
                                    journal = migrationJournal,
                                    householdId = householdId,
                                    accountUid = accountUid,
                                    snapshot = current.snapshot,
                                    enabled = journalReady && !journalError && !scheduleBusy &&
                                        canStartSharedCompletion(state, completion),
                                    onBusyChange = { migrationBusy = it }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = onClose,
                        enabled = completion !is SharedCompletionState.Sending && !scheduleBusy && !migrationBusy
                    ) { Text("닫기") }
                }
            )
        }
    }
}
