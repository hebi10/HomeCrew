package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

enum class StatisticsPeriod(val label: String) {
    WEEK("이번 주"),
    MONTH("이번 달")
}

@Composable
fun HomeCrewStatsScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    members: List<String> = listOf("나", "지민")
) {
    val palette = LocalHomeCrewColors.current
    var period by rememberSaveable { mutableStateOf(StatisticsPeriod.WEEK) }
    val periodChores = when (period) {
        StatisticsPeriod.WEEK -> tasksForWeek(chores, today)
        StatisticsPeriod.MONTH -> tasksForMonth(chores, today)
    }
    var selectedMember by rememberSaveable { mutableStateOf("전체") }
    val activeMember = selectedMember.takeIf { it in members } ?: "전체"
    val visibleChores = if (activeMember == "전체") periodChores else {
        periodChores.filter { it.definition.owner == activeMember }
    }
    val summary = summaryFor(visibleChores)
    val memberPoints = members.map { member ->
        member to periodChores.filter { it.definition.owner == member }
            .sumOf { it.definition.points }
    }
    val maxPoints = memberPoints.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val overdue = visibleChores.count { !it.completed && it.date.isBefore(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { ScreenHeader("통계", "집안일 완료 및 업무량") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticsPeriod.entries.forEach { choice ->
                    HomeCrewFilterChip(
                        label = choice.label,
                        selected = choice == period,
                        onClick = { period = choice }
                    )
                }
            }
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(members.size + 1) { index ->
                    val name = if (index == 0) "전체" else members[index - 1]
                    HomeCrewFilterChip(
                        label = name,
                        selected = activeMember == name,
                        onClick = { selectedMember = name }
                    )
                }
            }
        }
        item {
            Text(
                if (period == StatisticsPeriod.WEEK) "이번 주 월요일~일요일 · ${if (activeMember == "전체") "전체 구성원" else activeMember}" else
                    "이번 달 1일~말일 · ${if (activeMember == "전체") "전체 구성원" else activeMember}",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStatCard(
                    label = "완료율",
                    value = if (summary.total == 0) "–" else "${summary.percent}%",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = "완료한 집안일",
                    value = "${summary.completed}개",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStatCard(
                    label = "배정 업무량",
                    value = "${summary.assignedPoints}점",
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = "완료 업무량",
                    value = "${summary.completedPoints}점",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionHeading(if (period == StatisticsPeriod.WEEK) "요일별 완료 현황" else "주차별 완료 현황")
                    PeriodBarChart(
                        today = today,
                        period = period,
                        chores = visibleChores,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (period == StatisticsPeriod.WEEK) "막대는 월~일 각 날짜의 완료 건수입니다." else
                            "막대는 이번 달 각 주차에 예정된 회차 중 완료한 건수입니다.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionHeading("구성원별 배정 업무량")
                    memberPoints.forEach { (name, points) ->
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${points}점",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            LinearProgressIndicator(
                                progress = { points.toFloat() / maxPoints },
                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                color = palette.ink,
                                trackColor = palette.border
                            )
                        }
                    }
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("집계 기준")
                    Text(
                        "완료 ${summary.completed} / 대상 ${summary.total}건 · 지난 미완료 ${overdue}건",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "완료율 = 선택 기간의 완료 회차 ÷ 전체 예정 회차. 기한 초과는 오늘 이전의 미완료 회차이며, 미래 예정 회차도 기간 내 분모에 포함됩니다.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Text(
                "업무량은 집안일 난이도(1~5점)의 합계입니다. 실제 소요 시간과 같지는 않습니다.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (visibleChores.isEmpty()) {
            item {
                EmptyResult("통계 데이터가 없습니다", "집안일 기록이 있는 기간을 선택해보세요.")
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = modifier
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .background(palette.surface, RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PeriodBarChart(
    today: LocalDate,
    period: StatisticsPeriod,
    chores: List<DemoChore>,
    modifier: Modifier = Modifier
) {
    val palette = LocalHomeCrewColors.current
    val start = if (period == StatisticsPeriod.WEEK) weekStart(today) else today.withDayOfMonth(1)
    val bucketCount = if (period == StatisticsPeriod.WEEK) 7 else
        ((today.lengthOfMonth() + start.dayOfWeek.value - 2) / 7) + 1
    val counts = (0 until bucketCount).map { index ->
        chores.count { chore ->
            if (!chore.completed) false else if (period == StatisticsPeriod.WEEK) {
                chore.date == start.plusDays(index.toLong())
            } else {
                val weekIndex = (chore.date.dayOfMonth + start.dayOfWeek.value - 2) / 7
                weekIndex == index
            }
        }
    }
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        counts.forEachIndexed { index, count ->
            val barHeight = (count.toFloat() / maxCount * 80).dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                Box(
                    modifier = Modifier.height(82.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.56f)
                                .height(barHeight)
                                .background(palette.ink, RoundedCornerShape(2.dp))
                        )
                    }
                }
                Text(
                    if (period == StatisticsPeriod.WEEK) weekdayLabel(start.plusDays(index.toLong())) else "${index + 1}주",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewStatsLight() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = false) {
        HomeCrewStatsScreen(today, sampleChores(today))
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewStatsDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewStatsScreen(today, sampleChores(today))
    }
}
