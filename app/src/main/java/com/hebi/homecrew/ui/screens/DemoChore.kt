package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.model.completionPercent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ChoreDefinition(
    val id: String,
    val name: String,
    val category: String,
    val owner: String,
    val repeat: String,
    val points: Int,
    val time: String,
    val description: String = "",
    val recurrence: ChoreRepeat? = null,
    val assignment: ChoreAssignment? = null,
    val startDate: LocalDate? = null
)

data class DemoChore(
    val id: String,
    val definition: ChoreDefinition,
    val date: LocalDate,
    val completed: Boolean = false
)

data class ChoreSummary(
    val total: Int,
    val completed: Int,
    val assignedPoints: Int,
    val completedPoints: Int
) {
    val percent: Int get() = completionPercent(completed, total)
}

fun choreDefinitions(): List<ChoreDefinition> = listOf(
    ChoreDefinition("dishes", "설거지", "주방", "나", "매일", 2, "20:00"),
    ChoreDefinition("recycle", "분리수거", "쓰레기", "나", "화·금", 3, "21:00"),
    ChoreDefinition("bathroom", "욕실 청소", "청소", "지민", "주 1회", 4, "19:00"),
    ChoreDefinition("living", "거실 청소", "청소", "지민", "주 2회", 3, "10:00"),
    ChoreDefinition("laundry", "빨래 정리", "세탁", "나", "주 2회", 2, "14:00"),
    ChoreDefinition("foodwaste", "음식물 쓰레기", "쓰레기", "지민", "매일", 2, "13:00"),
    ChoreDefinition("plants", "식물 물주기", "생활", "나", "주 1회", 1, "09:00")
)

fun sampleChores(today: LocalDate): List<DemoChore> {
    val definitions = choreDefinitions().associateBy { it.id }
    fun chore(key: String, day: LocalDate, done: Boolean): DemoChore {
        val definition = requireNotNull(definitions[key])
        return DemoChore(
            id = "${key}-${day.toEpochDay()}",
            definition = definition,
            date = day,
            completed = done
        )
    }

    val records = mutableListOf<DemoChore>()
    for (offset in -14..14) {
        if (offset == 0) continue
        val day = today.plusDays(offset.toLong())
        val done = offset < 0
        records += chore("dishes", day, done && offset % 5 != 0)
        if (offset % 3 == 0) records += chore("recycle", day, done && offset % 2 == 0)
        if (offset % 7 == 0) records += chore("bathroom", day, done)
        if (offset % 4 == 0) records += chore("living", day, done)
        if (offset % 2 == 0) records += chore("laundry", day, done && offset % 3 != 0)
        if (offset % 2 != 0) records += chore("foodwaste", day, done)
        if (offset % 7 == 1) records += chore("plants", day, done)
    }

    records += listOf(
        chore("dishes", today, false),
        chore("recycle", today, false),
        chore("bathroom", today, false),
        chore("living", today, true),
        chore("laundry", today, true),
        chore("foodwaste", today, true),
        chore("plants", today, true)
    )
    return records.sortedWith(compareBy({ it.date }, { it.definition.time }, { it.id }))
}

fun demoChoreStatus(chore: DemoChore, today: LocalDate): ChoreStatus = when {
    chore.completed -> ChoreStatus.COMPLETED
    chore.date.isBefore(today) -> ChoreStatus.OVERDUE
    else -> ChoreStatus.PLANNED
}

fun summaryFor(chores: List<DemoChore>): ChoreSummary = ChoreSummary(
    total = chores.size,
    completed = chores.count { it.completed },
    assignedPoints = chores.sumOf { it.definition.points },
    completedPoints = chores.filter { it.completed }.sumOf { it.definition.points }
)

fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun tasksForDay(chores: List<DemoChore>, date: LocalDate): List<DemoChore> =
    chores.filter { it.date == date }.sortedBy { it.definition.time }

fun tasksForWeek(chores: List<DemoChore>, date: LocalDate): List<DemoChore> {
    val start = weekStart(date)
    return chores.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }
}

fun tasksForMonth(chores: List<DemoChore>, date: LocalDate): List<DemoChore> =
    chores.filter { it.date.year == date.year && it.date.month == date.month }

fun filteredDefinitions(
    all: List<ChoreDefinition>,
    instances: List<DemoChore>,
    query: String,
    category: String,
    status: String,
    today: LocalDate
): List<ChoreDefinition> {
    val todayById = tasksForDay(instances, today).associateBy { it.definition.id }
    return all.filter { definition ->
        val matchesText = definition.name.contains(query.trim(), ignoreCase = true)
        val matchesCategory = category == "전체" || definition.category == category
        val matchesStatus = status == "전체" || todayById[definition.id]?.let {
            demoChoreStatus(it, today).label == status
        } == true
        matchesText && matchesCategory && matchesStatus
    }
}
