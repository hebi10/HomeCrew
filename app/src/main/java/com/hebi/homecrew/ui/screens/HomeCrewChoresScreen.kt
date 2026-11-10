package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.components.HomeCrewStatusChip
import com.hebi.homecrew.ui.components.HomeCrewTextField
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

@Composable
fun HomeCrewChoresScreen(
    today: LocalDate,
    chores: List<DemoChore>,
    onDetails: (ChoreDefinition) -> Unit,
    onAdd: () -> Unit,
    definitions: List<ChoreDefinition> = chores.map { it.definition }.distinctBy { it.id }
) {
    val palette = LocalHomeCrewColors.current
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("전체") }
    var status by rememberSaveable { mutableStateOf("전체") }

    val visible = filteredDefinitions(
        all = definitions,
        instances = chores,
        query = search,
        category = category,
        status = status,
        today = today
    )
    val todayInstances = tasksForDay(chores, today).associateBy { it.definition.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = "집안일",
                subtitle = "반복 집안일 목록"
            ) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = "집안일 추가")
                }
            }
        }
        item { DemoNotice() }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf("전체") + definitions.map { it.category }.distinct()).forEach { option ->
                    HomeCrewFilterChip(
                        label = option,
                        selected = category == option,
                        onClick = { category = option }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("전체", "예정", "완료", "기한 초과").forEach { option ->
                    HomeCrewFilterChip(
                        label = option,
                        selected = status == option,
                        onClick = { status = option }
                    )
                }
            }
        }
        item {
            SectionHeading(
                "등록된 집안일",
                "${visible.size}개 · 오늘의 회차 기준 상태"
            )
        }
        if (visible.isEmpty()) {
            item {
                EmptyResult(
                    if (definitions.isEmpty()) "아직 등록된 집안일이 없습니다"
                    else "조건에 맞는 집안일이 없습니다",
                    if (definitions.isEmpty()) "집안일 추가 버튼을 눌러 시작하세요."
                    else "검색어나 필터를 변경해보세요."
                )
            }
        } else {
            items(visible, key = { it.id }) { definition ->
                val todayTask = todayInstances[definition.id]
                BorderedSection(
                    modifier = Modifier.clickable {
                        onDetails(definition)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                definition.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            todayTask?.let {
                                HomeCrewStatusChip(demoChoreStatus(it, today))
                            }
                        }
                        Text(
                            "${definition.repeat} · ${definition.category} · ${definition.owner}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                        Text(
                            "난이도 ${definition.points}점 · 예정 ${definition.time}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted
                        )
                    }
                }
            }
        }
        item {
            Text(
                "등록·수정·삭제 결과는 이 기기에 저장됩니다. 앱 재실행 후에도 유지됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoresLight() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = false) {
        HomeCrewChoresScreen(today, sampleChores(today), {}, {})
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoresDark() {
    val today = LocalDate.of(2026, 11, 10)
    HomeCrewTheme(darkTheme = true) {
        HomeCrewChoresScreen(today, sampleChores(today), {}, {})
    }
}
