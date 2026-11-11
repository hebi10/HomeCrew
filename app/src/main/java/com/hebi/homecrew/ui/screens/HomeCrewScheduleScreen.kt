package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

enum class CalendarMode(val title: String) {
    MONTH("월"),
    WEEK("주"),
    DAY("일")
}

internal const val ALL_SCHEDULE_MEMBERS = "전체"
private const val SCHEDULE_DAYS_AHEAD = 62L

internal data class ScheduleDaySummary(val total: Int, val completed: Int) {
    val statusLabel: String
        get() = when {
            total == 0 -> "–"
            completed == total -> "완료"
            completed == 0 -> "${total}건"
            else -> "${completed}/${total}"
        }
}

internal fun scheduleChoresForMember(
    chores: List<DemoChore>,
    member: String
): List<DemoChore> =
    if (member == ALL_SCHEDULE_MEMBERS) chores
    else chores.filter { it.definition.owner == member }

internal fun scheduleDaySummaries(chores: List<DemoChore>): Map<LocalDate, ScheduleDaySummary> =
    chores.groupBy { it.date }.mapValues { (_, tasks) ->
        ScheduleDaySummary(tasks.size, tasks.count { it.completed })
    }

internal fun movedScheduleDate(
    date: LocalDate,
    mode: CalendarMode,
    offset: Long
): LocalDate = when (mode) {
    CalendarMode.MONTH -> date.plusMonths(offset)
    CalendarMode.WEEK -> date.plusWeeks(offset)
    CalendarMode.DAY -> date.plusDays(offset)
}

internal fun canCreateScheduleChore(date: LocalDate, today: LocalDate): Boolean =
    !date.isBefore(today) && !date.isAfter(today.plusDays(SCHEDULE_DAYS_AHEAD))

@Composable
fun HomeCrewScheduleScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    members: List<String>,
    onToggle: (String) -> Unit,
    onDetails: (DemoChore) -> Unit,
    onAddForDate: (LocalDate, String?) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    var requestedMember by rememberSaveable { mutableStateOf(ALL_SCHEDULE_MEMBERS) }
    val selectedMember = requestedMember.takeIf { it in members } ?: ALL_SCHEDULE_MEMBERS
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val visibleChores = scheduleChoresForMember(chores, selectedMember)
    val summaries = scheduleDaySummaries(visibleChores)
    val selectedChores = tasksForDay(visibleChores, selected)
    val currentSummary = summaries[selected] ?: ScheduleDaySummary(0, 0)
    val canAdd = canCreateScheduleChore(selected, today)
    val dateRange = when (mode) {
        CalendarMode.MONTH -> koreanMonthLabel(selected)
        CalendarMode.WEEK -> {
            val start = weekStart(selected)
            "${start.monthValue}/${start.dayOfMonth} – ${start.plusDays(6).monthValue}/${start.plusDays(6).dayOfMonth}"
        }
        CalendarMode.DAY -> koreanDateLabel(selected)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space16
        ),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
    ) {
        item { ScreenHeader(title = "일정", subtitle = "날짜별 집안일 관리") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CalendarMode.entries.forEach { option ->
                    val active = mode == option
                    Box(
                        modifier = Modifier.weight(1f)
                            .heightIn(min = HomeCrewDimensions.touchTarget)
                            .background(
                                if (active) palette.ink else palette.surface,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable { mode = option }
                            .semantics {
                                contentDescription = "${option.title} 보기"
                                selected = active
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) palette.surface else palette.ink
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        selectedEpoch = movedScheduleDate(selected, mode, -1).toEpochDay()
                    }
                ) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 기간")
                }
                Text(dateRange, style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = {
                        selectedEpoch = movedScheduleDate(selected, mode, 1).toEpochDay()
                    }
                ) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 기간")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                Text(
                    "구성원별 일정",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.muted
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)
                ) {
                    (listOf(ALL_SCHEDULE_MEMBERS) + members.distinct()).forEach { name ->
                        HomeCrewFilterChip(
                            label = name,
                            selected = selectedMember == name,
                            onClick = { requestedMember = name }
                        )
                    }
                }
            }
        }
        when (mode) {
            CalendarMode.MONTH -> item {
                CalendarMonthGrid(
                    selected = selected,
                    today = today,
                    summaries = summaries,
                    onSelect = { selectedEpoch = it.toEpochDay() }
                )
            }
            CalendarMode.WEEK -> item {
                CalendarWeekStrip(
                    selected = selected,
                    today = today,
                    summaries = summaries,
                    onSelect = { selectedEpoch = it.toEpochDay() }
                )
            }
            CalendarMode.DAY -> Unit
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(koreanDateLabel(selected), style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { onAddForDate(selected, selectedMember.takeUnless { it == ALL_SCHEDULE_MEMBERS }) },
                    enabled = canAdd,
                    modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget).semantics {
                        contentDescription = "선택 날짜에 집안일 추가"
                    }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("추가")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "집안일 ${currentSummary.total}개 · 완료 ${currentSummary.completed}개",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { selectedEpoch = today.toEpochDay() },
                    modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
                ) { Text("오늘") }
            }
        }
        if (!canAdd) {
            item {
                Text(
                    if (selected.isBefore(today)) "지난 날짜에는 새 집안일을 추가할 수 없습니다."
                    else "집안일 추가는 오늘부터 62일 이내 날짜를 선택해 주세요.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (selectedChores.isEmpty()) {
            item {
                EmptyResult(
                    "선택한 날짜의 집안일이 없습니다",
                    if (canAdd) "위의 추가 버튼으로 이 날짜에 집안일을 등록할 수 있습니다."
                    else "다른 날짜를 선택해 주세요."
                )
            }
        } else {
            items(selectedChores, key = { it.id }) { task ->
                DemoChoreRow(
                    task = task,
                    today = today,
                    onToggle = { onToggle(task.id) },
                    onDetails = { onDetails(task) }
                )
            }
        }
    }
}

@Composable
private fun CalendarWeekStrip(
    selected: LocalDate,
    today: LocalDate,
    summaries: Map<LocalDate, ScheduleDaySummary>,
    onSelect: (LocalDate) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    val start = weekStart(selected)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(7) { index ->
            val day = start.plusDays(index.toLong())
            val highlighted = selected == day
            val summary = summaries[day] ?: ScheduleDaySummary(0, 0)
            val foreground = if (highlighted) palette.surface else palette.ink
            Column(
                modifier = Modifier.weight(1f)
                    .heightIn(min = 74.dp)
                    .background(
                        if (highlighted) palette.ink else palette.subtle,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelect(day) }
                    .semantics {
                        contentDescription =
                            "${koreanDateLabel(day)}, 집안일 ${summary.total}개, 완료 ${summary.completed}개${if (day == today) ", 오늘" else ""}"
                        selected = highlighted
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    weekdayLabel(day),
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground
                )
                Text(
                    "${day.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Medium,
                    color = foreground
                )
                Text(
                    summary.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlighted) foreground else palette.muted
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    selected: LocalDate,
    today: LocalDate,
    summaries: Map<LocalDate, ScheduleDaySummary>,
    onSelect: (LocalDate) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    val first = selected.withDayOfMonth(1)
    val leading = first.dayOfWeek.value - 1
    val days = selected.lengthOfMonth()
    val cells = ((leading + days + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach { name ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
            }
        }
        repeat(cells / 7) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(7) { column ->
                    val dayNumber = row * 7 + column - leading + 1
                    if (dayNumber !in 1..days) {
                        Box(Modifier.weight(1f).heightIn(min = 56.dp))
                    } else {
                        val day = first.withDayOfMonth(dayNumber)
                        val summary = summaries[day] ?: ScheduleDaySummary(0, 0)
                        val highlighted = day == selected
                        val foreground = if (highlighted) palette.surface else palette.ink
                        Column(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = 56.dp)
                                .background(
                                    if (highlighted) palette.ink else palette.subtle,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { onSelect(day) }
                                .semantics {
                                    contentDescription =
                                        "${koreanDateLabel(day)}, 집안일 ${summary.total}개, 완료 ${summary.completed}개${if (day == today) ", 오늘" else ""}"
                                    selected = highlighted
                                }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "${day.dayOfMonth}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = foreground
                            )
                            Text(
                                summary.statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highlighted) foreground else palette.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewScheduleLight() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = false) {
        HomeCrewScheduleScreen(today, sampleChores(today), listOf("나", "지민"), {}, {}, { _, _ -> })
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewScheduleDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewScheduleScreen(today, sampleChores(today), listOf("나", "지민"), {}, {}, { _, _ -> })
    }
}
