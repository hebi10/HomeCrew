package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewProgressCard
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode
import java.time.LocalDate

@Composable
fun HomeCrewHomeScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    currentMember: String,
    themeMode: ThemeMode,
    onMemberSelected: (String) -> Unit,
    onToggle: (String) -> Unit,
    onDetails: (DemoChore) -> Unit,
    onAdd: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onCycleTheme: () -> Unit,
    householdName: String = "우리 집",
    members: List<String> = listOf("나", "지민")
) {
    val palette = LocalHomeCrewColors.current
    val todayChores = tasksForDay(chores, today)
    val myChores = todayChores.filter { it.definition.owner == currentMember }
        .sortedBy { it.completed }
    val others = todayChores.filter { it.definition.owner != currentMember }
        .sortedBy { it.completed }
    val remaining = myChores.count { !it.completed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(
                title = householdName,
                subtitle = "${koreanDateLabel(today)} · 오늘"
            ) {
                IconButton(onClick = onCycleTheme) {
                    Icon(
                        Icons.Outlined.WbSunny,
                        contentDescription = "테마 전환 · 현재 ${themeMode.label}"
                    )
                }
                IconButton(onClick = onOpenDesignSystem) {
                    Icon(
                        Icons.Outlined.Palette,
                        contentDescription = "1-A 디자인 시스템 보기"
                    )
                }
            }
        }
        item { DemoNotice() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "현재 보는 구성원",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.muted
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { name ->
                        HomeCrewFilterChip(
                            label = name,
                            selected = currentMember == name,
                            onClick = { onMemberSelected(name) }
                        )
                    }
                }
            }
        }
        item {
            val summary = summaryFor(todayChores)
            HomeCrewProgressCard(summary.completed, summary.total)
        }
        item { SectionHeading("내가 할 일", "${remaining}개 남음") }
        if (myChores.isEmpty()) {
            item {
                EmptyResult("담당 집안일이 없습니다", "현재 구성원에게 배정된 작업이 없습니다.")
            }
        } else {
            items(myChores, key = { it.id }) { task ->
                DemoChoreRow(
                    task = task,
                    today = today,
                    onToggle = { onToggle(task.id) },
                    onDetails = { onDetails(task) }
                )
            }
        }
        item {
            SectionHeading(
                "다른 구성원의 집안일",
                "${others.count { !it.completed }}개 남음"
            )
        }
        if (others.isEmpty()) {
            item { EmptyResult("다른 집안일이 없습니다", "오늘은 다른 구성원의 작업이 없습니다.") }
        } else {
            items(others, key = { it.id }) { task ->
                DemoChoreRow(
                    task = task,
                    today = today,
                    onToggle = { onToggle(task.id) },
                    onDetails = { onDetails(task) }
                )
            }
        }
        item {
            HomeCrewPrimaryButton(
                text = "+ 집안일 추가",
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                "로컬 시연 화면 · 자동 배정과 저장은 후속 단계에서 구현합니다.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewHomeLight() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = false) {
        HomeCrewHomeScreen(
            today, sampleChores(today), "나", ThemeMode.LIGHT,
            {}, {}, {}, {}, {}, {}
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewHomeDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewHomeScreen(
            today, sampleChores(today), "나", ThemeMode.DARK,
            {}, {}, {}, {}, {}, {}
        )
    }
}
