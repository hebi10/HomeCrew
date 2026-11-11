package com.hebi.homecrew.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.components.HomeCrewStatusChip
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun HomeCrewChoreDetailScreen(
    definition: ChoreDefinition,
    chores: List<DemoChore>,
    today: LocalDate,
    selectedOccurrenceId: String?,
    actingMember: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectOccurrence: (String) -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalHomeCrewColors.current
    val occurrences = chores.filter { it.definition.id == definition.id }
    val selected = selectedChoreOccurrence(occurrences, selectedOccurrenceId, today)
    val upcoming = upcomingChoreOccurrences(occurrences, today)
    val missed = missedChoreOccurrences(occurrences, today)
    val finished = recentCompletedChoreOccurrences(occurrences)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showingDelete by rememberSaveable { mutableStateOf(false) }

    fun selectOccurrence(id: String) {
        onSelectOccurrence(id)
        scope.launch { listState.scrollToItem(2) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.background,
        contentColor = palette.ink
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                horizontal = HomeCrewDimensions.screenPadding,
                vertical = HomeCrewDimensions.space16
            ),
            verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
        ) {
            item { TextButton(onClick = onBack) { Text("이전") } }
            item {
                ScreenHeader(
                    title = definition.name,
                    subtitle = "${definition.category} · 업무량 ${definition.points}점"
                )
            }
            item {
                BorderedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("선택한 회차", style = MaterialTheme.typography.titleMedium)
                            selected?.let { HomeCrewStatusChip(demoChoreStatus(it, today)) }
                        }
                        if (selected == null) {
                            Text(
                                "조회 가능한 회차가 없습니다. 시작일과 반복 규칙을 확인해 주세요.",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "${selected.date} · ${selected.definition.time}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "예정 담당자: ${selected.definition.owner}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (selected.completed) {
                                Text(
                                    "실제 완료자: ${selected.completedBy?.takeIf { it.isNotBlank() } ?: "기록 없음"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                recordedCompletionTime(
                                    selected.completedAtEpochMillis, ZoneId.systemDefault()
                                )?.let { dateTime ->
                                    Text(
                                        "완료 시각: $dateTime",
                                        color = palette.muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                HomeCrewSecondaryButton(
                                    text = "이 회차 완료 취소",
                                    onClick = { onToggle(selected.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "완료를 취소하면 이 회차의 완료자·완료 시각 기록이 해제됩니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted
                                )
                            } else {
                                Text(
                                    "완료자 기록: $actingMember (현재 선택된 구성원)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted
                                )
                                HomeCrewPrimaryButton(
                                    text = "이 회차 완료 처리",
                                    onClick = { onToggle(selected.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "선택한 날짜의 회차만 완료 처리됩니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.muted
                                )
                            }
                        }
                    }
                }
            }
            if (definition.description.isNotBlank()) {
                item {
                    BorderedSection {
                        Text(definition.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                BorderedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                        SectionHeading("가까운 예정 회차", "${upcoming.size}건")
                        if (upcoming.isEmpty()) {
                            Text(
                                "앞으로 미완료 예정 회차가 없습니다.",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        upcoming.forEach { chore ->
                            ChoreOccurrenceSelectRow(
                                chore = chore,
                                today = today,
                                selected = chore.id == selected?.id,
                                onClick = { selectOccurrence(chore.id) }
                            )
                        }
                    }
                }
            }
            if (missed.isNotEmpty()) {
                item {
                    BorderedSection {
                        Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                            SectionHeading("지난 미완료 회차", "${missed.size}건 표시")
                            missed.forEach { chore ->
                                ChoreOccurrenceSelectRow(
                                    chore = chore,
                                    today = today,
                                    selected = chore.id == selected?.id,
                                    onClick = { selectOccurrence(chore.id) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                BorderedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                        SectionHeading("최근 완료 기록", "${finished.size}건")
                        if (finished.isEmpty()) {
                            Text(
                                "완료 기록이 없습니다.",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        finished.forEach { chore ->
                            ChoreOccurrenceSelectRow(
                                chore = chore,
                                today = today,
                                selected = chore.id == selected?.id,
                                onClick = { selectOccurrence(chore.id) }
                            )
                        }
                        if (finished.isNotEmpty()) {
                            Text(
                                "완료 기록을 선택하면 해당 회차로 이동하여 완료를 취소할 수 있습니다.",
                                color = palette.muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                BorderedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                        SectionHeading("집안일 규칙")
                        Text("반복: ${definition.repeat}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "담당 방식: ${definition.assignment?.mode?.label ?: "고정 담당"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "참여자: ${definition.assignment?.members?.joinToString(" · ") ?: definition.owner}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "시작일: ${definition.startDate?.toString() ?: "시작일 정보 없음"}",
                            color = palette.muted,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                    "수정 시 기존 완료·과거 회차를 보존하며, 삭제 시 해당 집안일의 관련 기록이 모두 제거됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
            }
        }
    }

    if (showingDelete) {
        AlertDialog(
            onDismissRequest = { showingDelete = false },
            title = { Text("집안일을 삭제할까요?") },
            text = {
                Text(
                    "${definition.name}의 예정 회차와 완료 기록을 이 기기에서 모두 삭제합니다. 되돌릴 수 없습니다."
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

@Composable
private fun ChoreOccurrenceSelectRow(
    chore: DemoChore,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    val status = demoChoreStatus(chore, today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HomeCrewDimensions.taskRowMinHeight)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "회차 선택: ${status.label} · ${chore.date}"
            }
            .padding(vertical = HomeCrewDimensions.space8),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${chore.date} · ${chore.definition.time}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                if (selected) "선택됨" else status.label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
        Text(
            if (chore.completed) {
                val performer = chore.completedBy?.takeIf { it.isNotBlank() }
                val doneAt = recordedCompletionTime(
                    chore.completedAtEpochMillis, ZoneId.systemDefault()
                )
                listOfNotNull(
                    "실제 완료자: ${performer ?: "기록 없음"}",
                    doneAt?.let { "완료 시각: $it" }
                ).joinToString(" · ")
            } else {
                "예정 담당자: ${chore.definition.owner}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted
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
            sampleChores(today), today, task.id, "나",
            {}, {}, {}, {}, {}
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
            sampleChores(today), today, task.id, "나",
            {}, {}, {}, {}, {}
        )
    }
}
