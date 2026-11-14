package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
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
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit = {},
    householdName: String = "우리 집",
    members: List<String> = listOf("나", "지민")
) {
    val palette = LocalHomeCrewColors.current
    val todayChores = tasksForDay(chores, today)
    val summary = summaryFor(todayChores)
    val myChores = todayChores.filter { it.definition.owner == currentMember }
        .sortedBy { it.completed }
    val others = todayChores.filter { it.definition.owner != currentMember }
        .sortedBy { it.completed }
    val remaining = myChores.count { !it.completed && !it.skipped }
    val myStatus = when {
        myChores.isEmpty() -> "오늘 배정 없음"
        remaining == 0 -> "모두 완료"
        else -> "${remaining}개 남음"
    }
    val otherRemaining = others.count { !it.completed && !it.skipped }
    val otherStatus = when {
        others.isEmpty() -> "오늘 배정 없음"
        otherRemaining == 0 -> "모두 완료"
        else -> "${otherRemaining}개 남음"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space16
        ),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
    ) {
        item {
            ScreenHeader(
                title = householdName,
                subtitle = "${koreanDateLabel(today)} · 오늘"
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "집 및 앱 설정")
                }
                IconButton(onClick = onCycleTheme) {
                    val themeIcon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                    }
                    Icon(
                        themeIcon,
                        contentDescription = "테마 전환 · 현재 ${themeMode.label}"
                    )
                }
            }
        }
        item {
            HomeCrewProgressCard(summary.completed, summary.total)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                Text(
                    "구성원",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.muted
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                    Text(
                        if (currentMember == "나") "내가 할 일" else "${currentMember}의 할 일",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        myStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("집안일 추가")
                }
            }
        }
        if (myChores.isEmpty()) {
            item {
                Text(
                    "선택한 구성원에게 배정된 집안일이 없습니다.",
                    modifier = Modifier.padding(vertical = HomeCrewDimensions.space12),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
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
        if (members.size > 1) {
            item {
                SectionHeading(
                    "다른 구성원의 집안일",
                    otherStatus
                )
            }
            if (others.isEmpty()) {
                item {
                    Text(
                        "오늘은 다른 구성원의 집안일이 없습니다.",
                        modifier = Modifier.padding(vertical = HomeCrewDimensions.space12),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
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
            {}, {}, {}, {}, {}
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
            {}, {}, {}, {}, {}
        )
    }
}
