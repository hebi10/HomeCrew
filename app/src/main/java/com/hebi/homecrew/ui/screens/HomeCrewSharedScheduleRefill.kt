package com.hebi.homecrew.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hebi.homecrew.data.cloud.RemoteHouseholdChoreGateway
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull

internal data class ScheduleRefillRequest(
    val choreId: String,
    val revision: Long,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val firstMissingDay: Long
)

internal fun missingSharedScheduleRequests(
    snapshot: RemoteSnapshot,
    accountUid: String,
    today: LocalDate
): List<ScheduleRefillRequest> {
    if (snapshot.mayBeStale || snapshot.members.none { it.uid == accountUid }) return emptyList()
    val members = snapshot.members.map { it.uid }.toSet()
    val existing = snapshot.occurrences.groupBy { it.definitionId }
        .mapValues { (_, rows) -> rows.map { it.scheduledEpochDay }.toSet() }
    val from = today.toEpochDay()
    val to = today.plusDays(62)
    return snapshot.chores.mapNotNull { chore ->
        val rule = chore.schedule ?: return@mapNotNull null
        if (chore.revision < 1 || !rule.memberUids.all { it in members }) return@mapNotNull null
        val saved = existing[chore.id].orEmpty()
        // Cancelled, skipped and completed rows are all existing records.
        // Never recreate or reset them from the client.
        val missing = nextChoreDates(
            LocalDate.ofEpochDay(rule.startEpochDay),
            ChoreRepeat(ChoreRepeatMode.valueOf(rule.repeatMode),
                rule.weekdays.map(DayOfWeek::of).toSet(), rule.intervalDays),
            today, to
        ).firstOrNull { it.toEpochDay() !in saved } ?: return@mapNotNull null
        ScheduleRefillRequest(chore.id, chore.revision, from, to.toEpochDay(), missing.toEpochDay())
    }.sortedWith(compareBy({ it.firstMissingDay }, { it.choreId }))
}

private data class RefillKey(val choreId: String, val revision: Long)
private const val MAX_REFILL_REQUESTS = 6

@Composable
internal fun SharedScheduleRefillEffect(
    gateway: RemoteHouseholdChoreGateway,
    householdId: String,
    accountUid: String,
    preview: SharedChorePreviewState,
    enabled: Boolean,
    retryToken: Int,
    onBusyChange: (Boolean) -> Unit,
    onMessage: (String?) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var day by remember { mutableStateOf(sharedChoreToday()) }
    val latestPreview by rememberUpdatedState(preview)
    val latestEnabled by rememberUpdatedState(enabled)
    val busyCallback by rememberUpdatedState(onBusyChange)
    val messageCallback by rememberUpdatedState(onMessage)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (foreground) day = sharedChoreToday()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        if (foreground) {
            while (true) {
                day = sharedChoreToday()
                delay(30_000)
            }
        }
    }

    // At most six calls per day/visit (or explicit retry). Snapshot changes from
    // a successful request do not restart the effect or repeat the same command.
    val attempted = remember(gateway, householdId, accountUid, day, retryToken) { mutableSetOf<RefillKey>() }
    var paused by remember(gateway, householdId, accountUid, day, retryToken) { mutableStateOf(false) }
    val ready = preview is SharedChorePreviewState.Ready
    LaunchedEffect(gateway, householdId, accountUid, day, retryToken, foreground, enabled, ready) {
        if (!foreground || !enabled || !ready || paused) return@LaunchedEffect
        try {
            snapshotFlow { (latestPreview as? SharedChorePreviewState.Ready)?.snapshot }
                .filterNotNull().collect { _ ->
                    if (paused) return@collect
                    while (latestEnabled && foreground && sharedChoreToday() == day) {
                        val current = (latestPreview as? SharedChorePreviewState.Ready)?.snapshot ?: break
                        val next = missingSharedScheduleRequests(current, accountUid, day).firstOrNull {
                            RefillKey(it.choreId, it.revision) !in attempted
                        } ?: break
                        if (attempted.size >= MAX_REFILL_REQUESTS) {
                            messageCallback("자동 보충 한도에 도달했습니다. 필요한 경우 이어서 보충해 주세요.")
                            paused = true
                            break
                        }
                        attempted.add(RefillKey(next.choreId, next.revision))
                        busyCallback(true)
                        try {
                            val result = withTimeoutOrNull(20_000) {
                                gateway.generateSchedule(householdId, next.choreId, next.revision,
                                    next.fromEpochDay, next.toEpochDay)
                            }
                            if (result == null) {
                                paused = true
                                messageCallback("일정 보충 결과를 확인하지 못했습니다. 다시 연결 후 확인해 주세요.")
                                break
                            }
                            if (result.created > 0) messageCallback("서버 일정 보충: 새 회차 ${result.created}개 응답")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            paused = true
                            messageCallback("일정 보충을 중단했습니다. 연결 또는 담당 구성원을 확인해 주세요.")
                            break
                        } finally {
                            busyCallback(false)
                        }
                    }
                }
        } finally {
            busyCallback(false)
        }
    }
}
