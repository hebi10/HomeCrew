package com.hebi.homecrew.data.local

import com.hebi.homecrew.ui.screens.ChoreAssignment
import com.hebi.homecrew.ui.screens.ChoreAssignmentMode
import com.hebi.homecrew.ui.screens.ChoreDefinition
import com.hebi.homecrew.ui.screens.ChoreEditorDraft
import com.hebi.homecrew.ui.screens.ChoreRepeat
import com.hebi.homecrew.ui.screens.ChoreRepeatMode
import com.hebi.homecrew.ui.screens.DemoChore
import com.hebi.homecrew.ui.screens.legacyRepeat
import com.hebi.homecrew.ui.screens.orderedParticipants
import com.hebi.homecrew.ui.screens.previewDraftInstances
import com.hebi.homecrew.ui.screens.repeatDescription
import com.hebi.homecrew.ui.screens.validationError
import com.hebi.homecrew.ui.screens.workloadBaseline
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.previewChoresForSetup
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HomeCrewSnapshot(
    val definitions: List<ChoreDefinition>,
    val chores: List<DemoChore>
)

class HomeCrewRoomRepository(
    private val dao: HomeCrewDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun observe(): Flow<HomeCrewSnapshot> = combine(
        dao.observeDefinitions(),
        dao.observeAssignmentMembers(),
        dao.observeOccurrences()
    ) { definitions, members, occurrences ->
        assembleSnapshot(HomeCrewRows(definitions, members, occurrences))
    }

    suspend fun snapshot(): HomeCrewSnapshot = assembleSnapshot(dao.readRows())

    suspend fun initializeIfNeeded(household: HouseholdSetup, today: LocalDate) {
        val initial = previewChoresForSetup(household, today)
        val timestamp = clock()
        val definitions = mutableListOf<ChoreDefinitionEntity>()
        val people = mutableListOf<ChoreAssignmentMemberEntity>()
        val instances = mutableListOf<ChoreOccurrenceEntity>()

        initial.forEach { original ->
            val task = original.definition
            val repeat = legacyRepeat(task.repeat, today)
            val assignment = ChoreAssignment(
                mode = ChoreAssignmentMode.FIXED,
                members = listOf(task.owner)
            )
            val definition = task.copy(
                recurrence = repeat,
                assignment = assignment,
                startDate = today
            )
            definitions += definition.asEntity(timestamp, timestamp)
            people += assignment.members.mapIndexed { position, member ->
                ChoreAssignmentMemberEntity(definition.id, position, member)
            }
            val draft = ChoreEditorDraft(
                editingId = definition.id,
                name = definition.name,
                category = definition.category,
                description = definition.description,
                points = definition.points,
                time = definition.time,
                startDate = today,
                repeat = repeat,
                assignment = assignment
            )
            val generated = previewDraftInstances(draft, today)
            val initialOccurrence = original.copy(definition = definition)
            instances += (listOf(initialOccurrence) + generated)
                .distinctBy { it.id }.map { it.asEntity() }
        }
        dao.seedOnce(definitions, people, instances)
        extendUpcoming(today)
    }

    suspend fun extendUpcoming(today: LocalDate) {
        val rows = dao.readRows()
        val definitions = assembleSnapshot(rows).definitions
        val storedByDefinition = rows.occurrences.groupBy { it.definitionId }
        val pointsByDefinition = rows.definitions.associate { it.id to it.points }
        val firstDay = today.toEpochDay()
        val loads = mutableMapOf<String, Int>()
        rows.occurrences.filter { row ->
            row.scheduledEpochDay >= firstDay &&
                row.scheduledEpochDay < today.plusDays(7).toEpochDay()
        }.forEach { row ->
            loads[row.assignedMember] = (loads[row.assignedMember] ?: 0) +
                (pointsByDefinition[row.definitionId] ?: 0)
        }

        val missing = mutableListOf<ChoreOccurrenceEntity>()
        val lastDay = today.plusDays(62)
        definitions.forEach { definition ->
            val rule = definition.recurrence ?: return@forEach
            val start = definition.startDate ?: today
            val dates = com.hebi.homecrew.ui.screens.nextChoreDates(
                start, rule, start, lastDay
            )
            if (dates.isEmpty()) return@forEach
            val participants = definition.assignment?.members.orEmpty()
                .ifEmpty { listOf(definition.owner) }
            val mode = definition.assignment?.mode ?: ChoreAssignmentMode.FIXED
            val stored = storedByDefinition[definition.id].orEmpty()
            val savedDays = stored.map { it.scheduledEpochDay }.toHashSet()
            val lastStored = stored.maxByOrNull { it.scheduledEpochDay }
            val lastIndex = lastStored?.let { last ->
                dates.indexOfFirst { date -> date.toEpochDay() == last.scheduledEpochDay }
            } ?: -1
            val lastMember = lastStored?.let { participants.indexOf(it.assignedMember) } ?: -1
            val rotationOffset = if (lastIndex >= 0 && lastMember >= 0) {
                (lastMember - lastIndex % participants.size + participants.size) % participants.size
            } else 0

            dates.forEachIndexed { index, date ->
                val epoch = date.toEpochDay()
                if (epoch < firstDay || epoch in savedDays) return@forEachIndexed
                val owner = when (mode) {
                    ChoreAssignmentMode.FIXED -> participants.first()
                    ChoreAssignmentMode.ROUND_ROBIN ->
                        participants[(index + rotationOffset) % participants.size]
                    ChoreAssignmentMode.BALANCED ->
                        participants.minBy { loads[it] ?: 0 }
                }
                missing += ChoreOccurrenceEntity(
                    id = "${definition.id}-$epoch",
                    definitionId = definition.id,
                    scheduledEpochDay = epoch,
                    assignedMember = owner,
                    workloadPoints = definition.points
                )
                loads[owner] = (loads[owner] ?: 0) + definition.points
            }
        }
        if (missing.isNotEmpty()) dao.insertMissingOccurrences(missing)
    }

    suspend fun save(
        draft: ChoreEditorDraft,
        validMembers: List<String>,
        existingChores: List<DemoChore>,
        today: LocalDate
    ): String {
        require(validationError(draft, validMembers) == null) { "유효하지 않은 집안일입니다." }
        val ordered = orderedParticipants(draft.assignment.members, validMembers)
        val normalized = draft.copy(assignment = draft.assignment.copy(members = ordered))
        val id = normalized.editingId ?: "custom-${UUID.randomUUID()}"
        val instances = previewDraftInstances(
            normalized.copy(editingId = id),
            today,
            workloadBaseline(existingChores, normalized, today)
        )
        val definition = instances.firstOrNull()?.definition ?: ChoreDefinition(
            id = id,
            name = normalized.name.trim(),
            category = normalized.category,
            owner = ordered.first(),
            repeat = repeatDescription(normalized.repeat),
            points = normalized.points,
            time = normalized.time,
            description = normalized.description.trim(),
            recurrence = normalized.repeat,
            assignment = normalized.assignment,
            startDate = normalized.startDate
        )
        val timestamp = clock()
        val createdAt = dao.findDefinition(id)?.createdAtEpochMillis ?: timestamp
        dao.saveSchedule(
            definition = definition.asEntity(createdAt, timestamp),
            members = ordered.mapIndexed { position, member ->
                ChoreAssignmentMemberEntity(id, position, member)
            },
            occurrences = instances.map { it.asEntity() },
            todayEpochDay = today.toEpochDay()
        )
        return id
    }

    suspend fun delete(id: String) {
        check(dao.deleteDefinition(id) == 1) { "삭제할 집안일을 찾을 수 없습니다." }
    }

    suspend fun toggleCompleted(id: String, actingMember: String) {
        val current = dao.findOccurrence(id) ?: error("완료할 회차를 찾을 수 없습니다.")
        val nextCompleted = !current.completed
        check(
            dao.setCompleted(
                occurrenceId = id,
                completed = nextCompleted,
                completedAtEpochMillis = if (nextCompleted) clock() else null,
                completedBy = if (nextCompleted) actingMember else null
            ) == 1
        ) { "완료 상태를 저장하지 못했습니다." }
    }

    private fun assembleSnapshot(rows: HomeCrewRows): HomeCrewSnapshot {
        val participants = rows.members.groupBy { it.definitionId }
        val definitions = rows.definitions.map { entity ->
            val members = participants[entity.id].orEmpty()
                .sortedBy { it.position }.map { it.memberName }
                .ifEmpty { listOf(entity.defaultOwner) }
            val repeat = ChoreRepeat(
                mode = ChoreRepeatMode.entries.firstOrNull { it.name == entity.repeatMode }
                    ?: ChoreRepeatMode.NONE,
                weekdays = DayOfWeek.entries.filter { day ->
                    entity.weekdayMask and (1 shl (day.value - 1)) != 0
                }.toSet(),
                intervalDays = entity.repeatIntervalDays
            )
            ChoreDefinition(
                id = entity.id,
                name = entity.name,
                category = entity.category,
                owner = entity.defaultOwner,
                repeat = entity.repeatLabel,
                points = entity.points,
                time = entity.scheduledTime,
                description = entity.description,
                recurrence = repeat,
                assignment = ChoreAssignment(
                    mode = ChoreAssignmentMode.entries.firstOrNull {
                        it.name == entity.assignmentMode
                    } ?: ChoreAssignmentMode.FIXED,
                    members = members
                ),
                startDate = entity.startEpochDay?.let(LocalDate::ofEpochDay)
            )
        }
        val byId = definitions.associateBy { it.id }
        val chores = rows.occurrences.mapNotNull { row ->
            val definition = byId[row.definitionId] ?: return@mapNotNull null
            DemoChore(
                id = row.id,
                definition = definition.copy(
                    owner = row.assignedMember,
                    points = row.workloadPoints.takeIf { it > 0 } ?: definition.points
                ),
                date = LocalDate.ofEpochDay(row.scheduledEpochDay),
                completed = row.completed,
                completedAtEpochMillis = row.completedAtEpochMillis,
                completedBy = row.completedBy
            )
        }
        return HomeCrewSnapshot(definitions, chores)
    }

    private fun ChoreDefinition.asEntity(
        createdAt: Long,
        updatedAt: Long
    ): ChoreDefinitionEntity {
        val repeatRule = recurrence ?: legacyRepeat(repeat, startDate ?: LocalDate.now())
        val assignmentRule = assignment ?: ChoreAssignment(members = listOf(owner))
        return ChoreDefinitionEntity(
            id = id,
            name = name,
            category = category,
            defaultOwner = owner,
            repeatLabel = repeatDescription(repeatRule),
            points = points,
            scheduledTime = time,
            description = description,
            startEpochDay = startDate?.toEpochDay(),
            repeatMode = repeatRule.mode.name,
            weekdayMask = repeatRule.weekdays.fold(0) { mask, day ->
                mask or (1 shl (day.value - 1))
            },
            repeatIntervalDays = repeatRule.intervalDays,
            assignmentMode = assignmentRule.mode.name,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt
        )
    }

    private fun DemoChore.asEntity(): ChoreOccurrenceEntity = ChoreOccurrenceEntity(
        id = id,
        definitionId = definition.id,
        scheduledEpochDay = date.toEpochDay(),
        assignedMember = definition.owner,
        workloadPoints = definition.points,
        completed = completed
    )
}
