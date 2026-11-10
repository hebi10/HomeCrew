package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@Composable
fun HomeCrewScheduleScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    onToggle: (String) -> Unit,
    onDetails: (DemoChore) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val selectedChores = tasksForDay(chores, selected)
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
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { ScreenHeader(title = "일정", subtitle = "날짜별 집안일 관리") }
        item { DemoNotice() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarMode.entries.forEach { option ->
                    HomeCrewFilterChip(
                        label = option.title,
                        selected = mode == option,
                        onClick = { mode = option }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    val result = when (mode) {
                        CalendarMode.MONTH -> selected.minusMonths(1)
                        CalendarMode.WEEK -> selected.minusWeeks(1)
                        CalendarMode.DAY -> selected.minusDays(1)
                    }
                    selectedEpoch = result.toEpochDay()
                }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 기간")
                }
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = {
                    val result = when (mode) {
                        CalendarMode.MONTH -> selected.plusMonths(1)
                        CalendarMode.WEEK -> selected.plusWeeks(1)
                        CalendarMode.DAY -> selected.plusDays(1)
                    }
                    selectedEpoch = result.toEpochDay()
                }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 기간")
                }
            }
        }
        item {
            when (mode) {
                CalendarMode.MONTH -> CalendarMonthGrid(
                    selected, today, chores, onSelect = { selectedEpoch = it.toEpochDay() }
                )
                CalendarMode.WEEK -> CalendarWeekStrip(
                    selected, today, chores, onSelect = { selectedEpoch = it.toEpochDay() }
                )
                CalendarMode.DAY -> {
                    Text(
                        text = "선택한 날짜의 예정·완료 작업을 확인하세요.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                    koreanDateLabel(selected),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { selectedEpoch = today.toEpochDay() }) {
                    Text("오늘")
                }
            }
        }
        item {
            Text(
                "집안일 ${selectedChores.size}개 · 완료 ${selectedChores.count { it.completed }}개",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (selectedChores.isEmpty()) {
            item {
                EmptyResult(
                    "선택한 날짜의 집안일이 없습니다",
                    "다른 날짜를 선택해보세요."
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
    chores: List<DemoChore>,
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
            val count = chores.count { it.date == day }
            val foreground = if (highlighted) palette.surface else palette.ink
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 74.dp)
                    .background(
                        if (highlighted) palette.ink else palette.subtle,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelect(day) }
                    .semantics {
                        contentDescription =
                            "${koreanDateLabel(day)}, 집안일 ${count}개${if (day == today) ", 오늘" else ""}"
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
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
                    if (count > 0) "${count}건" else "–",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) palette.surface else palette.muted
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    selected: LocalDate,
    today: LocalDate,
    chores: List<DemoChore>,
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
                        val count = chores.count { it.date == day }
                        val highlighted = day == selected
                        val foreground = if (highlighted) palette.surface else palette.ink
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .background(
                                    if (highlighted) palette.ink else palette.subtle,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { onSelect(day) }
                                .semantics {
                                    contentDescription =
                                        "${koreanDateLabel(day)}, 집안일 ${count}개${if (day == today) ", 오늘" else ""}"
                                }
                                .padding(vertical = 6.dp),
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
                                if (count > 0) "${count}건" else "–",
                                style = MaterialTheme.typography.bodySmall,
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
        HomeCrewScheduleScreen(today, sampleChores(today), {}, {})
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewScheduleDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewScheduleScreen(today, sampleChores(today), {}, {})
    }
}
