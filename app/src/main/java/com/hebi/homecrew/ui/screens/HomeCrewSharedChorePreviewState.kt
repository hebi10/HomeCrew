package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.cloud.RemoteHouseholdReadGateway
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import com.hebi.homecrew.data.cloud.requireValidCloudId
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal sealed interface SharedChorePreviewState {
    data object Loading : SharedChorePreviewState
    data object Offline : SharedChorePreviewState
    data object Unavailable : SharedChorePreviewState
    data class Ready(val snapshot: RemoteSnapshot) : SharedChorePreviewState
}

internal enum class SharedChoreStatus { PENDING, COMPLETED, SKIPPED, SCHEDULE_CANCELLED }

internal data class SharedChoreRow(
    val occurrenceId: String,
    val name: String,
    val category: String,
    val points: Int,
    val assignedToMe: Boolean,
    val status: SharedChoreStatus,
    val assigneeLabel: String? = null
)

internal data class SharedChoreDay(
    val rows: List<SharedChoreRow>,
    val missingDefinitionCount: Int
)

internal fun sharedChoreToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

internal fun observeSharedChorePreview(
    gateway: RemoteHouseholdReadGateway,
    householdId: String
): Flow<SharedChorePreviewState> {
    requireValidCloudId(householdId)
    return gateway.observe(householdId)
        .map<RemoteSnapshot, SharedChorePreviewState> { snapshot ->
            // No household records are exposed in the unverified cache state.
            if (snapshot.mayBeStale) SharedChorePreviewState.Offline
            else SharedChorePreviewState.Ready(snapshot)
        }
        .onStart { emit(SharedChorePreviewState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(SharedChorePreviewState.Unavailable)
        }
}

internal fun sharedChoreDay(
    snapshot: RemoteSnapshot,
    date: LocalDate,
    currentUid: String
): SharedChoreDay {
    require(!snapshot.mayBeStale)
    require(currentUid.isNotBlank())
    val definitions = snapshot.chores.associateBy { it.id }
    val occurrences = snapshot.occurrences.filter { it.scheduledEpochDay == date.toEpochDay() }
    val rows = occurrences.mapNotNull { occurrence ->
        val definition = definitions[occurrence.definitionId] ?: return@mapNotNull null
        SharedChoreRow(
            occurrenceId = occurrence.id,
            name = definition.name,
            category = definition.category,
            points = occurrence.workloadPoints ?: definition.points,
            assignedToMe = occurrence.assignedUid == currentUid,
            assigneeLabel = snapshot.members.firstOrNull { it.uid == occurrence.assignedUid }?.displayName
                ?: if (snapshot.members.isNotEmpty() && snapshot.members.none { it.uid == occurrence.assignedUid })
                    "탈퇴한 구성원" else null,
            status = when {
                occurrence.scheduleCancelled -> SharedChoreStatus.SCHEDULE_CANCELLED
                occurrence.skipped -> SharedChoreStatus.SKIPPED
                occurrence.completed -> SharedChoreStatus.COMPLETED
                else -> SharedChoreStatus.PENDING
            }
        )
    }.sortedWith(compareBy({ it.status.ordinal }, { !it.assignedToMe }, { it.name }, { it.occurrenceId }))
    return SharedChoreDay(rows, occurrences.size - rows.size)
}
