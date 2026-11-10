package com.hebi.homecrew.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.components.HomeCrewTextField
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun ChoreSubpageLayout(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalHomeCrewColors.current
    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(palette.surface)
                    .navigationBarsPadding().imePadding()
                    .padding(horizontal = HomeCrewDimensions.screenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error != null) {
                    Text(error, color = palette.error, style = MaterialTheme.typography.bodySmall)
                }
                HomeCrewPrimaryButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HomeCrewDimensions.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextButton(onClick = onBack) { Text("이전") }
            ScreenHeader(title = title, subtitle = subtitle)
            content()
        }
    }
}

@Composable
fun HomeCrewChoreFormScreen(
    draft: ChoreEditorDraft,
    today: LocalDate,
    members: List<String>,
    onChange: (ChoreEditorDraft) -> Unit,
    onRepeat: () -> Unit,
    onAssignment: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    error: String?,
    baseline: Map<String, Int> = emptyMap()
) {
    val context = LocalContext.current
    val palette = LocalHomeCrewColors.current
    val preview = previewDraftInstances(draft, today, baseline, daysAhead = 370).take(3)
    ChoreSubpageLayout(
        title = if (draft.editingId == null) "집안일 추가" else "집안일 수정",
        subtitle = "내용을 입력하고 반복·담당 방식을 설정하세요.",
        primaryLabel = "시연 집안일 저장",
        onBack = onBack,
        onPrimary = onSave,
        error = error
    ) {
        HomeCrewTextField(
            label = "집안일 이름 *",
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            placeholder = "예: 설거지",
            isError = error?.contains("이름") == true
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("카테고리", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                choreCategories.forEach { category ->
                    HomeCrewFilterChip(
                        label = category,
                        selected = category == draft.category,
                        onClick = { onChange(draft.copy(category = category)) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            label = { Text("설명 (선택)") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${draft.description.length}/300자") },
            isError = draft.description.length > 300
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("예상 업무량", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { point ->
                    HomeCrewFilterChip(
                        label = "$point",
                        selected = draft.points == point,
                        onClick = { onChange(draft.copy(points = point)) }
                    )
                }
            }
            Text(
                "업무량은 1~5점으로 표기하며 실제 걸리는 시간을 뜻하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = palette.muted
            )
        }
        BorderedSection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("반복", style = MaterialTheme.typography.titleMedium)
                Text(repeatDescription(draft.repeat), style = MaterialTheme.typography.bodyMedium)
                HomeCrewSecondaryButton(
                    text = "반복 설정",
                    onClick = onRepeat,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "반복 설정 열기"
                    }
                )
            }
        }
        BorderedSection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("담당 방식", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${draft.assignment.mode.label} · ${draft.assignment.members.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodyMedium
                )
                HomeCrewSecondaryButton(
                    text = "담당 방식 설정",
                    onClick = onAssignment,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "담당 방식 설정 열기"
                    }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("시작일 · 예정 시간", style = MaterialTheme.typography.titleMedium)
            HomeCrewSecondaryButton(
                text = "시작일: ${draft.startDate}",
                onClick = {
                    val start = draft.startDate
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onChange(draft.copy(startDate = LocalDate.of(year, month + 1, day)))
                        },
                        start.year, start.monthValue - 1, start.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            )
            HomeCrewSecondaryButton(
                text = "예정 시간: ${draft.time}",
                onClick = {
                    val components = draft.time.split(":")
                    val hour = components.getOrNull(0)?.toIntOrNull() ?: 20
                    val minute = components.getOrNull(1)?.toIntOrNull() ?: 0
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            onChange(draft.copy(time = String.format(Locale.US, "%02d:%02d", h, m)))
                        },
                        hour, minute, true
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        BorderedSection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다가오는 일정 미리보기", style = MaterialTheme.typography.titleMedium)
                if (preview.isEmpty()) {
                    Text("미리보기 기간에 예정된 집안일이 없습니다.")
                } else {
                    preview.forEach { occurrence ->
                        Text(
                            "${occurrence.date} · ${occurrence.definition.time} · ${occurrence.definition.owner}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        Text(
            "이 단계의 등록·수정은 앱 실행 중 시연 데이터에만 반영됩니다. 기기에 실제로 저장하는 기능은 2-A에서 연결합니다.",
            style = MaterialTheme.typography.bodySmall, color = palette.muted
        )
    }
}

@Composable
fun HomeCrewRepeatScreen(
    initial: ChoreRepeat,
    startDate: LocalDate,
    onApply: (ChoreRepeat) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    var mode by rememberSaveable { mutableStateOf(initial.mode.name) }
    var days by rememberSaveable { mutableStateOf(initial.weekdays.map { it.name }) }
    var interval by rememberSaveable { mutableIntStateOf(initial.intervalDays) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val frequency = ChoreRepeatMode.valueOf(mode)
    val rule = ChoreRepeat(frequency, days.map(DayOfWeek::valueOf).toSet(), interval)
    val preview = nextChoreDates(startDate, rule, startDate, startDate.plusDays(400)).take(3)

    ChoreSubpageLayout(
        title = "반복 설정",
        subtitle = "빈도와 요일을 선택하고 다음 일정을 확인하세요.",
        primaryLabel = "반복 설정 적용",
        onBack = onBack,
        onPrimary = {
            if (frequency in setOf(ChoreRepeatMode.WEEKLY, ChoreRepeatMode.BIWEEKLY) && days.isEmpty()) {
                error = "반복할 요일을 하나 이상 선택해 주세요."
            } else {
                error = null
                onApply(rule)
            }
        },
        error = error
    ) {
        ChoreRepeatMode.entries.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = frequency == option,
                    onClick = {
                        mode = option.name
                        error = null
                        if (option in listOf(ChoreRepeatMode.WEEKLY, ChoreRepeatMode.BIWEEKLY)
                            && days.isEmpty()
                        ) days = listOf(startDate.dayOfWeek.name)
                    }
                )
                TextButton(onClick = { mode = option.name; error = null }) {
                    Text(option.label)
                }
            }
        }
        if (frequency in setOf(ChoreRepeatMode.WEEKLY, ChoreRepeatMode.BIWEEKLY)) {
            Text("반복 요일", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                choreWeekdays.forEach { day ->
                    HomeCrewFilterChip(
                        label = choreDayName(day),
                        selected = day.name in days,
                        onClick = {
                            days = if (day.name in days) days - day.name else days + day.name
                            error = null
                        }
                    )
                }
            }
        }
        if (frequency == ChoreRepeatMode.CUSTOM) {
            Text("반복 간격: ${interval}일", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeCrewSecondaryButton(
                    text = "간격 -",
                    onClick = { interval = (interval - 1).coerceAtLeast(1) }
                )
                HomeCrewSecondaryButton(
                    text = "간격 +",
                    onClick = { interval = (interval + 1).coerceAtMost(30) }
                )
            }
        }
        if (frequency == ChoreRepeatMode.MONTHLY) {
            Text(
                "29·30·31일에 시작하면 해당 날짜가 없는 달에는 그 달 마지막 날로 조정합니다.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        BorderedSection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다음 3회 일정", style = MaterialTheme.typography.titleMedium)
                preview.forEach { Text(it.toString()) }
            }
        }
    }
}

@Composable
fun HomeCrewAssignmentScreen(
    initial: ChoreAssignment,
    members: List<String>,
    points: Int,
    baseline: Map<String, Int>,
    onApply: (ChoreAssignment) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    var mode by rememberSaveable { mutableStateOf(initial.mode.name) }
    var selected by rememberSaveable { mutableStateOf(initial.members) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val choice = ChoreAssignmentMode.valueOf(mode)
    val rule = ChoreAssignment(choice, selected)
    val upcoming = assignedOwners(rule, 3, points, baseline)

    ChoreSubpageLayout(
        title = "담당 방식",
        subtitle = "고정 담당자 또는 구성원 사이의 분담 규칙을 정합니다.",
        primaryLabel = "담당 방식 적용",
        onBack = onBack,
        onPrimary = {
            error = if (selected.isEmpty()) "참여자를 한 명 이상 선택해 주세요."
                else if (choice == ChoreAssignmentMode.FIXED && selected.size != 1)
                    "고정 담당자는 한 명만 선택해 주세요."
                else null
            if (error == null) onApply(rule)
        },
        error = error
    ) {
        ChoreAssignmentMode.entries.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = choice == option,
                    onClick = {
                        mode = option.name
                        if (option == ChoreAssignmentMode.FIXED)
                            selected = listOf(selected.firstOrNull() ?: members.first())
                    }
                )
                TextButton(onClick = {
                    mode = option.name
                    if (option == ChoreAssignmentMode.FIXED)
                        selected = listOf(selected.firstOrNull() ?: members.first())
                }) { Text(option.label) }
            }
        }
        val explanation = when (choice) {
            ChoreAssignmentMode.FIXED -> "선택한 한 명에게 모든 예정 회차를 배정합니다."
            ChoreAssignmentMode.ROUND_ROBIN -> "선택한 구성원의 표시 순서대로 회차마다 돌아가며 배정합니다."
            ChoreAssignmentMode.BALANCED ->
                "다른 예정 집안일의 업무량을 기준으로 적게 배정된 구성원을 먼저 선택합니다."
        }
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        Text("참여 구성원", style = MaterialTheme.typography.titleMedium)
        members.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (choice == ChoreAssignmentMode.FIXED) {
                    RadioButton(
                        selected = selected.firstOrNull() == member,
                        onClick = { selected = listOf(member); error = null }
                    )
                } else {
                    Checkbox(
                        checked = member in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + member else selected - member
                            error = null
                        }
                    )
                }
                Text(member, style = MaterialTheme.typography.bodyLarge)
            }
        }
        BorderedSection {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다음 3회 배정 미리보기", style = MaterialTheme.typography.titleMedium)
                if (upcoming.isEmpty()) Text("참여자를 선택해 주세요.")
                upcoming.forEachIndexed { index, member ->
                    Text("${index + 1}회차 · $member", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "자동 분담은 현재 시연 상태의 예상 결과입니다. 영구적인 배정 기록은 2-A 이후 지원합니다.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewChoreFormLight() {
    HomeCrewTheme(darkTheme = false) {
        HomeCrewChoreFormScreen(
            ChoreEditorDraft(startDate = LocalDate.of(2026, 11, 10),
                assignment = ChoreAssignment(members = listOf("나"))),
            LocalDate.of(2026, 11, 10),
            listOf("나", "지민"), {}, {}, {}, {}, {}, null
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewRepeatDark() {
    HomeCrewTheme(darkTheme = true) {
        HomeCrewRepeatScreen(
            ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(DayOfWeek.MONDAY)),
            LocalDate.of(2026, 11, 10), {}, {}
        )
    }
}
