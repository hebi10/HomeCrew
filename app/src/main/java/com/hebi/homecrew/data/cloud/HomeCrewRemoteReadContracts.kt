package com.hebi.homecrew.data.cloud

import kotlinx.coroutines.flow.Flow

internal data class RemoteChore(
    val id: String,
    val name: String,
    val category: String,
    val points: Int,
    val assigneeUid: String,
    val revision: Long
)

internal data class RemoteOccurrence(
    val id: String,
    val definitionId: String,
    val scheduledEpochDay: Long,
    val assignedUid: String,
    val completed: Boolean,
    val completedByUid: String?,
    val revision: Long,
    val skipped: Boolean = false
)

internal data class RemoteSnapshot(
    val chores: List<RemoteChore>,
    val occurrences: List<RemoteOccurrence>,
    val mayBeStale: Boolean
)

internal interface RemoteHouseholdReadGateway {
    fun observe(householdId: String): Flow<RemoteSnapshot>
}

internal data class RemoteCompletionCommand(
    val occurrenceId: String,
    val completed: Boolean,
    val expectedRevision: Long,
    val requestId: String
) {
    init {
        requireValidCloudId(occurrenceId)
        require(expectedRevision in 1 until Long.MAX_VALUE)
        require(requestId.matches(Regex("[a-zA-Z0-9_-]{20,72}")))
    }
}

internal sealed interface RemoteCompletionResult {
    data class Accepted(val revision: Long) : RemoteCompletionResult
    data object Conflict : RemoteCompletionResult
    data object Rejected : RemoteCompletionResult
    data object Unknown : RemoteCompletionResult
}

internal interface RemoteHouseholdChoreGateway : RemoteHouseholdReadGateway {
    suspend fun recordCompletion(
        householdId: String,
        command: RemoteCompletionCommand
    ): RemoteCompletionResult
}
