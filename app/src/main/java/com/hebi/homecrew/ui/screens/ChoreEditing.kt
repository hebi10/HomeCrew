package com.hebi.homecrew.ui.screens

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

enum class ChoreRepeatMode(val label: String) {
    NONE("반복 안 함"),
    DAILY("매일"),
    WEEKLY("매주"),
    BIWEEKLY("격주"),
    MONTHLY("매월"),
    CUSTOM("사용자 지정")
}

enum class ChoreAssignmentMode(val label: String) {
    FIXED("고정 담당"),
    ROUND_ROBIN("순번제"),
    BALANCED("업무량 균형")
}

data class ChoreRepeat(
    val mode: ChoreRepeatMode = ChoreRepeatMode.NONE,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int = 2
)

data class ChoreAssignment(
    val mode: ChoreAssignmentMode = ChoreAssignmentMode.FIXED,
    val members: List<String> = listOf("나")
)

data class ChoreEditorDraft(
    val editingId: String? = null,
    val name: String = "",
    val category: String = "청소",
    val description: String = "",
    val points: Int = 2,
    val time: String = "20:00",
    val startDate: LocalDate,
    val repeat: ChoreRepeat = ChoreRepeat(),
    val assignment: ChoreAssignment
)

val choreCategories = listOf("주방", "청소", "세탁", "쓰레기", "생활", "반려동물")
val choreWeekdays = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

fun choreDayName(day: DayOfWeek): String =
    listOf("월", "화", "수", "목", "금", "토", "일")[day.value - 1]

fun repeatDescription(rule: ChoreRepeat): String = when (rule.mode) {
    ChoreRepeatMode.NONE -> "반복 안 함"
    ChoreRepeatMode.DAILY -> "매일"
    ChoreRepeatMode.WEEKLY, ChoreRepeatMode.BIWEEKLY -> {
        val days = choreWeekdays.filter { it in rule.weekdays }.joinToString("·") { choreDayName(it) }
        "${rule.mode.label} · ${days.ifBlank { "요일 미선택" }}"
    }
    ChoreRepeatMode.MONTHLY -> "매월 · 시작일 기준 (월말 보정)"
    ChoreRepeatMode.CUSTOM -> "${rule.intervalDays}일마다"
}

fun validationError(draft: ChoreEditorDraft, members: List<String>): String? = when {
    draft.name.trim().isEmpty() -> "집안일 이름을 입력해 주세요."
    draft.name.trim().length > 40 -> "집안일 이름은 40자 이내로 입력해 주세요."
    draft.category !in choreCategories -> "카테고리를 선택해 주세요."
    draft.description.length > 300 -> "설명은 300자 이내로 입력해 주세요."
    draft.points !in 1..5 -> "업무량은 1~5점 사이에서 선택해 주세요."
    !draft.time.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) -> "예정 시간을 확인해 주세요."
    draft.repeat.mode in setOf(ChoreRepeatMode.WEEKLY, ChoreRepeatMode.BIWEEKLY) &&
        draft.repeat.weekdays.isEmpty() -> "반복할 요일을 하나 이상 선택해 주세요."
    draft.repeat.mode == ChoreRepeatMode.CUSTOM && draft.repeat.intervalDays !in 1..30 ->
        "반복 간격은 1~30일입니다."
    draft.assignment.members.isEmpty() -> "담당 구성원을 선택해 주세요."
    draft.assignment.members.distinct().size != draft.assignment.members.size ->
        "담당 구성원이 중복되었습니다."
    !draft.assignment.members.all { it in members } -> "등록된 구성원만 담당자로 선택할 수 있습니다."
    draft.assignment.mode == ChoreAssignmentMode.FIXED && draft.assignment.members.size != 1 ->
        "고정 담당자는 한 명만 선택해 주세요."
    else -> null
}

fun nextChoreDates(
    startDate: LocalDate,
    rule: ChoreRepeat,
    from: LocalDate,
    to: LocalDate
): List<LocalDate> {
    if (from.isAfter(to)) return emptyList()
    val actualStart = if (startDate.isAfter(from)) startDate else from
    val weeklyStart = weekStart(startDate)
    return generateSequence(actualStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .filter { date ->
            val days = ChronoUnit.DAYS.between(startDate, date)
            when (rule.mode) {
                ChoreRepeatMode.NONE -> date == startDate
                ChoreRepeatMode.DAILY -> true
                ChoreRepeatMode.WEEKLY -> date.dayOfWeek in rule.weekdays
                ChoreRepeatMode.BIWEEKLY -> date.dayOfWeek in rule.weekdays &&
                    ChronoUnit.WEEKS.between(weeklyStart, weekStart(date)) % 2 == 0L
                ChoreRepeatMode.MONTHLY -> {
                    val months = ChronoUnit.MONTHS.between(
                        YearMonth.from(startDate), YearMonth.from(date)
                    )
                    months >= 0 && date.dayOfMonth == min(startDate.dayOfMonth, date.lengthOfMonth())
                }
                ChoreRepeatMode.CUSTOM -> days % rule.intervalDays.coerceIn(1, 30) == 0L
            }
        }.toList()
}

fun assignedOwners(
    rule: ChoreAssignment,
    count: Int,
    points: Int,
    baseline: Map<String, Int> = emptyMap()
): List<String> {
    val members = rule.members.distinct()
    if (members.isEmpty()) return emptyList()
    val loads = members.associateWith { baseline[it] ?: 0 }.toMutableMap()
    return (0 until count).map { index ->
        val member = when (rule.mode) {
            ChoreAssignmentMode.FIXED -> members.first()
            ChoreAssignmentMode.ROUND_ROBIN -> members[index % members.size]
            ChoreAssignmentMode.BALANCED -> members.minBy { loads.getValue(it) }
        }
        loads[member] = loads.getValue(member) + points
        member
    }
}

fun previewDraftInstances(
    draft: ChoreEditorDraft,
    today: LocalDate,
    baseline: Map<String, Int> = emptyMap(),
    daysAhead: Long = 62
): List<DemoChore> {
    val dates = nextChoreDates(draft.startDate, draft.repeat, today, today.plusDays(daysAhead))
    val people = assignedOwners(draft.assignment, dates.size, draft.points, baseline)
    val id = draft.editingId ?: "preview"
    return dates.mapIndexed { index, date ->
        val definition = ChoreDefinition(
            id = id,
            name = draft.name.trim(),
            category = draft.category,
            owner = people[index],
            repeat = repeatDescription(draft.repeat),
            points = draft.points,
            time = draft.time,
            description = draft.description.trim(),
            recurrence = draft.repeat,
            assignment = draft.assignment,
            startDate = draft.startDate
        )
        DemoChore("${id}-${date.toEpochDay()}", definition, date)
    }
}

fun legacyRepeat(label: String, anchor: LocalDate): ChoreRepeat {
    val days = choreWeekdays.filter { choreDayName(it) in label }
    return when {
        label == "매일" -> ChoreRepeat(ChoreRepeatMode.DAILY)
        label.contains("화·금") -> ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        label.startsWith("주 2회") -> ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(anchor.dayOfWeek, anchor.plusDays(3).dayOfWeek))
        label.startsWith("주 1회") -> ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(anchor.dayOfWeek))
        label.contains("매월") -> ChoreRepeat(ChoreRepeatMode.MONTHLY)
        days.isNotEmpty() -> ChoreRepeat(ChoreRepeatMode.WEEKLY, days.toSet())
        else -> ChoreRepeat(ChoreRepeatMode.NONE)
    }
}

fun draftFromDefinition(
    definition: ChoreDefinition,
    today: LocalDate,
    members: List<String>
): ChoreEditorDraft {
    val owner = definition.owner.takeIf { it in members } ?: members.firstOrNull() ?: "나"
    return ChoreEditorDraft(
        editingId = definition.id,
        name = definition.name,
        category = definition.category,
        description = definition.description,
        points = definition.points,
        time = definition.time,
        startDate = definition.startDate ?: today,
        repeat = definition.recurrence ?: legacyRepeat(definition.repeat, today),
        assignment = definition.assignment ?: ChoreAssignment(members = listOf(owner))
    )
}
