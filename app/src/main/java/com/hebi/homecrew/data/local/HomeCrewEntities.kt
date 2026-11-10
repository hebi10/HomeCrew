package com.hebi.homecrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chore_definitions")
data class ChoreDefinitionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val defaultOwner: String,
    val repeatLabel: String,
    val points: Int,
    val scheduledTime: String,
    val description: String,
    val startEpochDay: Long?,
    val repeatMode: String,
    val weekdayMask: Int,
    val repeatIntervalDays: Int,
    val assignmentMode: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "chore_assignment_members",
    primaryKeys = ["definitionId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ChoreDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["definitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("definitionId")]
)
data class ChoreAssignmentMemberEntity(
    val definitionId: String,
    val position: Int,
    val memberName: String
)

@Entity(
    tableName = "chore_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ChoreDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["definitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["definitionId", "scheduledEpochDay"], unique = true)]
)
data class ChoreOccurrenceEntity(
    @PrimaryKey val id: String,
    val definitionId: String,
    val scheduledEpochDay: Long,
    val assignedMember: String,
    @ColumnInfo(defaultValue = "0") val workloadPoints: Int = 0,
    val completed: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val completedBy: String? = null,
    val skippedAtEpochMillis: Long? = null,
    val skipReason: String? = null
)

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String,
    val value: String
)
