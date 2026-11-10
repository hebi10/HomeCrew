package com.hebi.homecrew.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.components.HomeCrewStatusChip
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

@Composable
fun HomeCrewChoreDetailScreen(
    definition: ChoreDefinition,
    chores: List<DemoChore>,
    today: LocalDate,
    selectedOccurrenceId: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalHomeCrewColors.current
    val occurrences = chores.filter { it.definition.id == definition.id }.sortedBy { it.date }
    val selected = occurrences.firstOrNull { it.id == selectedOccurrenceId }
        ?: occurrences.firstOrNull { it.date == today }
        ?: occurrences.firstOrNull { !it.date.isBefore(today) }
        ?: occurrences.lastOrNull()
    val upcoming = occurrences.filter { !it.date.isBefore(today) }.take(3)
    val finished = occurrences.filter { it.completed }.takeLast(3).reversed()
    var showingDelete by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("이전") } }
        item {
            ScreenHeader(
                title = definition.name,
                subtitle = "${definition.category} · 난이도 ${definition.points}점"
            )
        }
        item { DemoNotice() }
        if (definition.description.isNotBlank()) {
            item {
                BorderedSection {
                    Text(definition.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("회차 현황")
                    if (selected == null) {
                        Text("조회 가능한 예정 회차가 없습니다.", color = palette.muted)
                    } else {
                        HomeCrewStatusChip(demoChoreStatus(selected, today))
                        Text(
                            "${selected.date} · ${selected.definition.time}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "담당: ${selected.definition.owner}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HomeCrewPrimaryButton(
                            text = if (selected.completed) "이 회차 완료 취소" else "이 회차 완료 처리",
                            onClick = { onToggle(selected.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("집안일 규칙")
                    Text("반복: ${definition.repeat}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "담당 방식: ${definition.assignment?.mode?.label ?: "기존 데모의 고정 담당"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "참여자: ${definition.assignment?.members?.joinToString(" · ") ?: definition.owner}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "시작일: ${definition.startDate?.toString() ?: "기존 시연 데이터"}",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("가까운 예정 회차")
                    if (upcoming.isEmpty()) Text("앞으로 예정된 회차가 없습니다.", color = palette.muted)
                    upcoming.forEach { chore ->
                        Text(
                            "${chore.date} · ${chore.definition.owner} · ${if (chore.completed) "완료" else "예정"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("최근 완료 기록")
                    if (finished.isEmpty()) Text("완료 기록이 없습니다.", color = palette.muted)
                    finished.forEach { chore ->
                        Text(
                            "${chore.date} · ${chore.definition.owner} · 완료",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        item {
            HomeCrewSecondaryButton(
                text = "집안일 수정",
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            TextButton(onClick = { showingDelete = true }) {
                Text("집안일 삭제", color = palette.error)
            }
        }
        item {
            Text(
                "등록·수정·완료·삭제는 시연 세션에서만 적용됩니다. " +
                    "수정 시 기존 완료·과거 회차를 보존하며, 삭제 시 해당 집안일의 시연 기록을 모두 제거합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
    }

    if (showingDelete) {
        AlertDialog(
            onDismissRequest = { showingDelete = false },
            title = { Text("집안일을 삭제할까요?") },
            text = {
                Text(
                    "${definition.name}의 예정 회차와 완료 기록을 현재 시연 세션에서 모두 삭제합니다. 되돌릴 수 없습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showingDelete = false
                    onDelete()
                }) {
                    Text("삭제", color = palette.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingDelete = false }) { Text("취소") }
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoreDetailLight() {
    val today = LocalDate.of(2026, 11, 10)
    val task = sampleChores(today).first { it.date == today }
    HomeCrewTheme(darkTheme = false) {
        HomeCrewChoreDetailScreen(
            task.definition,
            sampleChores(today), today, task.id,
            {}, {}, {}, {}
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoreDetailDark() {
    val today = LocalDate.of(2026, 11, 10)
    val task = sampleChores(today).first { it.date == today }
    HomeCrewTheme(darkTheme = true) {
        HomeCrewChoreDetailScreen(
            task.definition,
            sampleChores(today), today, task.id,
            {}, {}, {}, {}
        )
    }
}
