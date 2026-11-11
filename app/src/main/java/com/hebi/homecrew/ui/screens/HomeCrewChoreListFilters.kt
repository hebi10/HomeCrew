package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.model.ChoreStatus
import java.time.LocalDate

internal const val CHORE_LIST_ALL = "전체"

internal enum class ChoreListRepeatFilter(val label: String) {
    ALL("전체"),
    RECURRING("반복"),
    ONE_TIME("일회성")
}

internal enum class ChoreListSort(val label: String) {
    NAME("이름순"),
    TIME("시간순"),
    WORKLOAD("업무량순")
}

internal data class ChoreListSelection(
    val query: String = "",
    val category: String = CHORE_LIST_ALL,
    val status: String = CHORE_LIST_ALL,
    val member: String = CHORE_LIST_ALL,
    val repeat: ChoreListRepeatFilter = ChoreListRepeatFilter.ALL,
    val sort: ChoreListSort = ChoreListSort.NAME
) {
    val hasFilters: Boolean
        get() = query.isNotBlank() || category != CHORE_LIST_ALL ||
            status != CHORE_LIST_ALL || member != CHORE_LIST_ALL ||
            repeat != ChoreListRepeatFilter.ALL || sort != ChoreListSort.NAME
}

internal data class ChoreListEntry(
    val definition: ChoreDefinition,
    val todayOccurrence: DemoChore?,
    val nextOccurrence: DemoChore?,
    val overdueOccurrence: DemoChore?,
    val overdueCount: Int
) {
    val participants: List<String>
        get() = definition.assignment?.members?.takeIf { it.isNotEmpty() }?.distinct()
            ?: listOf(definition.owner)

    val todayStatus: ChoreStatus?
        get() = todayOccurrence?.let {
            if (it.completed) ChoreStatus.COMPLETED else ChoreStatus.PLANNED
        }

    fun statusForDisplay(filter: String): ChoreStatus? =
        if (filter == ChoreStatus.OVERDUE.label && overdueCount > 0) ChoreStatus.OVERDUE
        else todayStatus ?: if (overdueCount > 0) ChoreStatus.OVERDUE else null

    fun occurrenceForDetails(filter: String): String? =
        if (filter == ChoreStatus.OVERDUE.label && overdueOccurrence != null) {
            overdueOccurrence.id
        } else {
            todayOccurrence?.id ?: nextOccurrence?.id ?: overdueOccurrence?.id
        }
}

internal fun isRecurringChore(definition: ChoreDefinition, today: LocalDate): Boolean {
    val mode = definition.recurrence?.mode
        ?: legacyRepeat(definition.repeat, definition.startDate ?: today).mode
    return mode != ChoreRepeatMode.NONE
}

internal fun choreListEntries(
    definitions: List<ChoreDefinition>,
    instances: List<DemoChore>,
    today: LocalDate
): List<ChoreListEntry> {
    val occurrencesByDefinition = instances.groupBy { it.definition.id }
    return definitions.distinctBy { it.id }.map { definition ->
        val records = occurrencesByDefinition[definition.id].orEmpty()
        val overdue = records.filter { !it.completed && it.date.isBefore(today) }
        ChoreListEntry(
            definition = definition,
            todayOccurrence = records.firstOrNull { it.date == today },
            nextOccurrence = records.filter { !it.date.isBefore(today) }
                .minWithOrNull(compareBy({ it.date }, { it.definition.time }, { it.id })),
            overdueOccurrence = overdue.maxWithOrNull(
                compareBy({ it.date }, { it.definition.time }, { it.id })
            ),
            overdueCount = overdue.size
        )
    }
}

internal fun filterChoreList(
    definitions: List<ChoreDefinition>,
    instances: List<DemoChore>,
    today: LocalDate,
    selection: ChoreListSelection
): List<ChoreListEntry> {
    val query = selection.query.trim()
    val matched = choreListEntries(definitions, instances, today).filter { entry ->
        val definition = entry.definition
        val matchesText = definition.name.contains(query, ignoreCase = true)
        val matchesCategory = selection.category == CHORE_LIST_ALL ||
            definition.category == selection.category
        val matchesMember = selection.member == CHORE_LIST_ALL ||
            selection.member in entry.participants
        val isRecurring = isRecurringChore(definition, today)
        val matchesRepeat = when (selection.repeat) {
            ChoreListRepeatFilter.ALL -> true
            ChoreListRepeatFilter.RECURRING -> isRecurring
            ChoreListRepeatFilter.ONE_TIME -> !isRecurring
        }
        val matchesStatus = when (selection.status) {
            CHORE_LIST_ALL -> true
            ChoreStatus.PLANNED.label -> entry.todayStatus == ChoreStatus.PLANNED
            ChoreStatus.COMPLETED.label -> entry.todayStatus == ChoreStatus.COMPLETED
            ChoreStatus.OVERDUE.label -> entry.overdueCount > 0
            else -> false
        }
        matchesText && matchesCategory && matchesMember && matchesRepeat && matchesStatus
    }
    return when (selection.sort) {
        ChoreListSort.NAME -> matched.sortedWith(
            compareBy({ it.definition.name }, { it.definition.id })
        )
        ChoreListSort.TIME -> matched.sortedWith(
            compareBy({ it.definition.time }, { it.definition.name }, { it.definition.id })
        )
        ChoreListSort.WORKLOAD -> matched.sortedWith(
            compareByDescending<ChoreListEntry> { it.definition.points }
                .thenBy { it.definition.name }.thenBy { it.definition.id }
        )
    }
}
