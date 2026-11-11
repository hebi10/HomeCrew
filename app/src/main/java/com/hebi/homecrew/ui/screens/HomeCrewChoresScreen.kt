package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewEmptyState
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.components.HomeCrewStatusChip
import com.hebi.homecrew.ui.components.HomeCrewTextField
import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

@Composable
fun HomeCrewChoresScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    onDetails: (ChoreDefinition, String?) -> Unit,
    onAdd: () -> Unit,
    definitions: List<ChoreDefinition> = chores.map { it.definition }.distinctBy { it.id },
    members: List<String> = listOf("나", "지민")
) {
    val palette = LocalHomeCrewColors.current
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CHORE_LIST_ALL) }
    var status by rememberSaveable { mutableStateOf(CHORE_LIST_ALL) }
    var member by rememberSaveable { mutableStateOf(CHORE_LIST_ALL) }
    var repeatFilter by rememberSaveable { mutableStateOf(ChoreListRepeatFilter.ALL) }
    var sortMode by rememberSaveable { mutableStateOf(ChoreListSort.NAME) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val visibleCategory = category.takeIf { selected ->
        selected == CHORE_LIST_ALL || definitions.any { it.category == selected }
    } ?: CHORE_LIST_ALL
    val visibleMember = member.takeIf { it == CHORE_LIST_ALL || it in members }
        ?: CHORE_LIST_ALL
    val selection = ChoreListSelection(
        query = search,
        category = visibleCategory,
        status = status,
        member = visibleMember,
        repeat = repeatFilter,
        sort = sortMode
    )
    val visible = filterChoreList(definitions, chores, today, selection)

    fun clearFilters() {
        search = ""
        category = CHORE_LIST_ALL
        status = CHORE_LIST_ALL
        member = CHORE_LIST_ALL
        repeatFilter = ChoreListRepeatFilter.ALL
        sortMode = ChoreListSort.NAME
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
                title = "집안일",
                subtitle = "등록한 집안일과 담당·반복 규칙"
            ) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = "집안일 추가")
                }
            }
        }
        if (definitions.isEmpty()) {
            item {
                HomeCrewEmptyState(
                    title = "아직 등록된 집안일이 없습니다",
                    description = "집안일을 추가해 담당자와 반복 일정을 관리해 보세요.",
                    actionLabel = "집안일 추가",
                    onAction = onAdd
                )
            }
        } else {
            item {
                HomeCrewTextField(
                    label = "집안일 검색",
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "이름으로 검색"
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)
                ) {
                    listOf(
                        CHORE_LIST_ALL,
                        ChoreStatus.PLANNED.label,
                        ChoreStatus.COMPLETED.label,
                        ChoreStatus.OVERDUE.label
                    ).forEach { option ->
                        HomeCrewFilterChip(
                            label = option,
                            selected = status == option,
                            onClick = { status = option }
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
                    Text("등록된 집안일", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { filtersExpanded = !filtersExpanded },
                        modifier = Modifier.semantics {
                            contentDescription = "필터 및 정렬"
                        }
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null)
                        Text("필터 및 정렬")
                    }
                }
                Text(
                    "${visible.size}개 표시 · 전체 ${definitions.size}개 등록된 작업 원본",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                Text(
                    "예정·완료는 오늘 회차, 기한 초과는 지난 미완료 회차 기준",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                if (selection.hasFilters) {
                    TextButton(onClick = ::clearFilters) {
                        Text("필터 초기화")
                    }
                }
            }
            if (filtersExpanded) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(palette.surface)
                            .border(1.dp, palette.border)
                            .padding(HomeCrewDimensions.space12),
                        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
                    ) {
                        ChoreListChoiceGroup(
                            title = "카테고리",
                            options = listOf(CHORE_LIST_ALL) +
                                definitions.map { it.category }.distinct().sorted(),
                            selected = visibleCategory,
                            onSelect = { category = it }
                        )
                        ChoreListChoiceGroup(
                            title = "구성원",
                            options = listOf(CHORE_LIST_ALL) + members.distinct(),
                            selected = visibleMember,
                            onSelect = { member = it }
                        )
                        ChoreListChoiceGroup(
                            title = "반복 여부",
                            options = ChoreListRepeatFilter.entries.map { it.label },
                            selected = repeatFilter.label,
                            onSelect = { selected ->
                                repeatFilter = ChoreListRepeatFilter.entries.first {
                                    it.label == selected
                                }
                            }
                        )
                        ChoreListChoiceGroup(
                            title = "정렬",
                            options = ChoreListSort.entries.map { it.label },
                            selected = sortMode.label,
                            onSelect = { selected ->
                                sortMode = ChoreListSort.entries.first {
                                    it.label == selected
                                }
                            }
                        )
                    }
                }
            } else if (visibleCategory != CHORE_LIST_ALL ||
                visibleMember != CHORE_LIST_ALL ||
                repeatFilter != ChoreListRepeatFilter.ALL ||
                sortMode != ChoreListSort.NAME
            ) {
                item {
                    val chosen = listOfNotNull(
                        visibleCategory.takeUnless { it == CHORE_LIST_ALL },
                        visibleMember.takeUnless { it == CHORE_LIST_ALL },
                        repeatFilter.label.takeUnless { repeatFilter == ChoreListRepeatFilter.ALL },
                        sortMode.label.takeUnless { sortMode == ChoreListSort.NAME }
                    )
                    Text(
                        chosen.joinToString(" · "),
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    HomeCrewEmptyState(
                        title = "조건에 맞는 집안일이 없습니다",
                        description = "검색어나 필터를 바꿔 보세요.",
                        actionLabel = "필터 초기화",
                        onAction = ::clearFilters
                    )
                }
            } else {
                items(visible, key = { it.definition.id }) { entry ->
                    ChoreDefinitionListRow(
                        entry = entry,
                        selectedStatus = status,
                        onDetails = {
                            onDetails(entry.definition, entry.occurrenceForDetails(status))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoreListChoiceGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = palette.muted
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)
        ) {
            options.forEach { option ->
                HomeCrewFilterChip(
                    label = option,
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun ChoreDefinitionListRow(
    entry: ChoreListEntry,
    selectedStatus: String,
    onDetails: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    val definition = entry.definition
    val statusToShow = entry.statusForDisplay(selectedStatus)
    val assignment = if (entry.participants.size == 1) {
        "담당 ${entry.participants.first()}"
    } else {
        "참여 ${entry.participants.joinToString("·")}"
    }
    val todayAssigned = if (entry.participants.size > 1 && entry.todayOccurrence != null) {
        " · 오늘 ${entry.todayOccurrence.definition.owner}"
    } else ""
    val nextDate = if (entry.todayOccurrence == null && entry.nextOccurrence != null) {
        " · 다음 ${entry.nextOccurrence.date}"
    } else ""

    BorderedSection(
        modifier = Modifier
            .heightIn(min = HomeCrewDimensions.taskRowMinHeight)
            .clickable(onClick = onDetails)
            .semantics {
                contentDescription = "집안일 상세: ${definition.name}"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    definition.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (statusToShow != null) {
                    HomeCrewStatusChip(statusToShow)
                } else {
                    Text(
                        "오늘 회차 없음",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                }
            }
            Text(
                "${definition.category} · ${definition.repeat} · ${assignment}${todayAssigned}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "예정 ${definition.time} · 업무량 ${definition.points}점${nextDate}" +
                    if (entry.overdueCount > 0) " · 지난 미완료 ${entry.overdueCount}건" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.overdueCount > 0) palette.warning else palette.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoresLight() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = false) {
        HomeCrewChoresScreen(today, sampleChores(today), { _, _ -> }, {})
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoresDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewChoresScreen(today, sampleChores(today), { _, _ -> }, {})
    }
}
