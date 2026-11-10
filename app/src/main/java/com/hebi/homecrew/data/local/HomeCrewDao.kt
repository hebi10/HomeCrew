package com.hebi.homecrew.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
data class HomeCrewRows(
    val definitions: List<ChoreDefinitionEntity>,
    val members: List<ChoreAssignmentMemberEntity>,
    val occurrences: List<ChoreOccurrenceEntity>
)

private const val SEED_KEY = "homecrew.initial_household.v1"

abstract class HomeCrewDao {
    @Query("SELECT * FROM chore_definitions ORDER BY name COLLATE NOCASE, id")
    abstract fun observeDefinitions(): Flow<List<ChoreDefinitionEntity>>

    @Query("SELECT * FROM chore_assignment_members ORDER BY definitionId, position")
    abstract fun observeAssignmentMembers(): Flow<List<ChoreAssignmentMemberEntity>>

    @Query("SELECT * FROM chore_occurrences ORDER BY scheduledEpochDay, id")
    abstract fun observeOccurrences(): Flow<List<ChoreOccurrenceEntity>>

    @Query("SELECT * FROM chore_definitions")
    abstract suspend fun listDefinitions(): List<ChoreDefinitionEntity>

    @Query("SELECT * FROM chore_assignment_members ORDER BY definitionId, position")
    abstract suspend fun listAssignmentMembers(): List<ChoreAssignmentMemberEntity>

    @Query("SELECT * FROM chore_occurrences ORDER BY scheduledEpochDay, id")
    abstract suspend fun listOccurrences(): List<ChoreOccurrenceEntity>

    @Query("SELECT * FROM chore_occurrences WHERE id = :id LIMIT 1")
    abstract suspend fun findOccurrence(id: String): ChoreOccurrenceEntity?

    @Query("SELECT * FROM chore_definitions WHERE id = :id LIMIT 1")
    abstract suspend fun findDefinition(id: String): ChoreDefinitionEntity?

    @Query("SELECT value FROM app_state WHERE key = :key LIMIT 1")
    abstract suspend fun readState(key: String): String?

    @Upsert
    abstract suspend fun upsertState(value: AppStateEntity)

    @Upsert
    abstract suspend fun upsertDefinition(value: ChoreDefinitionEntity)

    @Upsert
    abstract suspend fun upsertDefinitions(values: List<ChoreDefinitionEntity>)

    @Upsert
    abstract suspend fun upsertAssignmentMembers(values: List<ChoreAssignmentMemberEntity>)

    @Upsert
    abstract suspend fun upsertOccurrences(values: List<ChoreOccurrenceEntity>)

    @Query("DELETE FROM chore_assignment_members WHERE definitionId = :id")
    abstract suspend fun deleteAssignmentMembers(id: String)

    @Query(
        "DELETE FROM chore_occurrences WHERE definitionId = :id " +
            "AND scheduledEpochDay >= :todayEpochDay AND completed = 0"
    )
    abstract suspend fun deleteFutureIncompleteOccurrences(id: String, todayEpochDay: Long)

    @Query(
        "SELECT scheduledEpochDay FROM chore_occurrences WHERE definitionId = :id " +
            "AND (scheduledEpochDay < :todayEpochDay OR completed = 1)"
    )
    abstract suspend fun preservedDays(id: String, todayEpochDay: Long): List<Long>

    @Query("DELETE FROM chore_definitions WHERE id = :id")
    abstract suspend fun deleteDefinition(id: String): Int

    @Query(
        "UPDATE chore_occurrences SET completed = :completed, " +
            "completedAtEpochMillis = :completedAtEpochMillis, completedBy = :completedBy " +
            "WHERE id = :occurrenceId"
    )
    abstract suspend fun setCompleted(
        occurrenceId: String,
        completed: Boolean,
        completedAtEpochMillis: Long?,
        completedBy: String?
    ): Int

    @Transaction
    open suspend fun readRows(): HomeCrewRows = HomeCrewRows(
        definitions = listDefinitions(),
        members = listAssignmentMembers(),
        occurrences = listOccurrences()
    )

    @Transaction
    open suspend fun seedOnce(
        definitions: List<ChoreDefinitionEntity>,
        members: List<ChoreAssignmentMemberEntity>,
        occurrences: List<ChoreOccurrenceEntity>
    ) {
        if (readState(SEED_KEY) != null) return
        if (listDefinitions().isEmpty()) {
            upsertDefinitions(definitions)
            upsertAssignmentMembers(members)
            upsertOccurrences(occurrences)
        }
        upsertState(AppStateEntity(SEED_KEY, "1"))
    }

    @Transaction
    open suspend fun saveSchedule(
        definition: ChoreDefinitionEntity,
        members: List<ChoreAssignmentMemberEntity>,
        occurrences: List<ChoreOccurrenceEntity>,
        todayEpochDay: Long
    ) {
        require(members.isNotEmpty())
        require(members.all { it.definitionId == definition.id })
        require(occurrences.all { it.definitionId == definition.id })

        upsertDefinition(definition)
        deleteAssignmentMembers(definition.id)
        upsertAssignmentMembers(members)
        val retainedDays = preservedDays(definition.id, todayEpochDay).toHashSet()
        deleteFutureIncompleteOccurrences(definition.id, todayEpochDay)
        upsertOccurrences(occurrences.filterNot { it.scheduledEpochDay in retainedDays })
    }
}
