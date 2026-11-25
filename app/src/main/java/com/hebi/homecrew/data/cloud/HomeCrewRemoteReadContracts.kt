package com.hebi.homecrew.data.cloud

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

internal data class RemoteChore(
    val id: String,
    val name: String,
    val category: String,
    val points: Int,
    val assigneeUid: String,
    val revision: Long,
    val schedule: RemoteSchedule? = null
)

internal data class RemoteOccurrence(
    val id: String,
    val definitionId: String,
    val scheduledEpochDay: Long,
    val assignedUid: String,
    val completed: Boolean,
    val completedByUid: String?,
    val revision: Long,
    val skipped: Boolean = false,
    val workloadPoints: Int? = null,
    val scheduleCancelled: Boolean = false
)

internal data class RemoteSnapshot(
    val chores: List<RemoteChore>,
    val occurrences: List<RemoteOccurrence>,
    val mayBeStale: Boolean,
    val members: List<RemoteHouseholdMember> = emptyList()
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
    suspend fun createChore(
        householdId: String, choreId: String, name: String, category: String,
        description: String, points: Int, assigneeUid: String
    ): Long

    suspend fun configureSchedule(
        householdId: String, choreId: String, expectedRevision: Long, schedule: RemoteSchedule
    ): Long

    suspend fun generateSchedule(
        householdId: String, choreId: String, expectedRevision: Long,
        fromEpochDay: Long, toEpochDay: Long
    ): RemoteGenerationResult

    suspend fun previewFutureSchedule(
        householdId: String, choreId: String, expectedRevision: Long
    ): RemoteFutureSchedulePreview

    suspend fun applyFutureSchedule(
        householdId: String, choreId: String, expectedRevision: Long, previewDigest: String
    ): RemoteFutureSchedulePreview

    suspend fun importLocalManifest(manifest: LocalMigrationManifest): RemoteMigrationReceipt

    suspend fun migrationReceipt(householdId: String, digest: String): RemoteMigrationReceipt?

    suspend fun migrationStage(householdId: String): RemoteMigrationStage?
    suspend fun beginMigrationStage(upload: StagedMigrationUpload): RemoteMigrationStage
    suspend fun appendMigrationChunk(householdId: String, sessionId: String, chunk: MigrationUploadChunk): RemoteMigrationStage
    suspend fun sealMigrationStage(householdId: String, sessionId: String): RemoteMigrationStage
    suspend fun publishMigrationChunk(householdId: String, stage: RemoteMigrationStage)
    suspend fun rollbackMigrationPublication(householdId: String, sessionId: String)
    suspend fun cancelMigrationStage(householdId: String, sessionId: String)
    suspend fun cleanupMigrationStage(householdId: String, sessionId: String)

    suspend fun recordCompletion(
        householdId: String,
        command: RemoteCompletionCommand
    ): RemoteCompletionResult
}

internal data class RemoteHouseholdMember(
    val uid: String,
    val isOwner: Boolean,
    val displayName: String? = null
)

internal fun sharedMemberLabel(member: RemoteHouseholdMember, index: Int, accountUid: String): String {
    val name = member.displayName ?: if (member.isOwner) "소유자" else "구성원 $index"
    return name + (if (member.uid == accountUid) " (나)" else "")
}

internal data class RemoteSchedule(
    val startEpochDay: Long,
    val repeatMode: String,
    val weekdays: List<Int> = emptyList(),
    val intervalDays: Int = 2,
    val assignmentMode: String,
    val memberUids: List<String>
) {
    init {
        require(startEpochDay in LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.of(2200, 12, 31).toEpochDay())
        require(repeatMode in setOf("NONE", "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "CUSTOM"))
        require(assignmentMode in setOf("FIXED", "ROUND_ROBIN", "BALANCED"))
        require(weekdays == weekdays.distinct().sorted() && weekdays.all { it in 1..7 })
        require(if (repeatMode in setOf("WEEKLY", "BIWEEKLY")) weekdays.isNotEmpty() else weekdays.isEmpty())
        require(intervalDays in 1..30 && (repeatMode == "CUSTOM" || intervalDays == 2))
        require(memberUids.size in 1..8 && memberUids.distinct() == memberUids)
        require(memberUids.all { it.isNotBlank() && it.length <= 128 && '/' !in it && it != "." && it != ".." })
        require(assignmentMode != "FIXED" || memberUids.size == 1)
    }

    fun payload(): Map<String, Any> = mapOf(
        "startEpochDay" to startEpochDay, "repeatMode" to repeatMode,
        "weekdays" to weekdays, "intervalDays" to intervalDays,
        "assignmentMode" to assignmentMode, "memberUids" to memberUids
    )
}

internal data class RemoteGenerationResult(val created: Int, val existing: Int)

internal data class RemoteFutureScheduleChange(val date: Long, val type: String)

internal data class RemoteFutureSchedulePreview(
    val digest: String,
    val cancelled: Int,
    val reassigned: Int,
    val restored: Int,
    val protectedCount: Int,
    val balancedDeferred: Int,
    val changes: List<RemoteFutureScheduleChange>
) {
    init {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(listOf(cancelled, reassigned, restored, protectedCount, balancedDeferred).all { it in 0..62 })
        require(changes.size == cancelled + reassigned + restored && changes.size <= 62)
        require(changes.all { it.type in setOf("CANCEL", "REASSIGN", "RESTORE") })
    }
}

internal data class RemoteMigrationReceipt(
    val digest: String,
    val definitions: Int,
    val occurrences: Int,
    val completed: Int,
    val skipped: Int
) {
    init {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(definitions in 1..STAGED_MAX_DEFINITIONS && occurrences in 0..STAGED_MAX_OCCURRENCES)
        require(completed in 0..occurrences && skipped in 0..occurrences && completed + skipped <= occurrences)
    }
}
