package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.homecrew.data.cloud.RemoteHouseholdChoreGateway
import com.hebi.homecrew.data.cloud.RemoteSchedule
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun HomeCrewSharedSchedulePanel(
    gateway: RemoteHouseholdChoreGateway,
    householdId: String,
    accountUid: String,
    snapshot: RemoteSnapshot,
    enabled: Boolean,
    onBusyChange: (Boolean) -> Unit
) {
    if (snapshot.mayBeStale || snapshot.members.none { it.uid == accountUid }) return
    val palette = LocalHomeCrewColors.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("청소") }
    var newPoints by rememberSaveable { mutableStateOf("2") }
    var creationId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var creationStarted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }

    LaunchedEffect(snapshot, creationStarted, creationId) {
        if (creationStarted) {
            val stored = snapshot.chores.firstOrNull { it.id == creationId }
            if (stored != null && stored.name == newName.trim() &&
                stored.category == newCategory.trim() && stored.points == newPoints.toIntOrNull() &&
                stored.assigneeUid == accountUid
            ) {
                selectedId = stored.id
                newName = ""
                creationStarted = false
                creationId = UUID.randomUUID().toString()
                message = "새 집안일을 확인했습니다. 반복 규칙을 설정해 주세요."
            }
        }
    }

    fun execute(action: suspend () -> String) {
        if (!enabled || busy) return
        busy = true
        onBusyChange(true)
        message = null
        scope.launch {
            try {
                message = withTimeoutOrNull(35_000) { action() }
                    ?: "요청 결과를 확인하지 못했습니다. 최신 목록을 확인해 주세요."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "요청이 거부되었거나 연결 상태가 변경되었습니다. 최신 정보를 확인해 주세요."
            } finally {
                busy = false
                onBusyChange(false)
            }
        }
    }

    TextButton(
        onClick = { expanded = !expanded },
        enabled = !busy,
        modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
    ) { Text(if (expanded) "공동 일정 설정 닫기" else "공동 일정 설정") }
    if (!expanded) return

    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
        Text("공동 집안일 등록", style = MaterialTheme.typography.titleMedium)
        Text("먼저 집안일을 등록하고 반복 규칙을 선택합니다. 기존 로컬 집안일은 변경되지 않습니다.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall)
        val editing = enabled && !busy && !creationStarted
        OutlinedTextField(
            value = newName, onValueChange = { if (it.length <= 40) newName = it },
            label = { Text("집안일 이름") }, enabled = editing, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newCategory, onValueChange = { if (it.length <= 64) newCategory = it },
            label = { Text("카테고리") }, enabled = editing, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newPoints, onValueChange = { if (it.length <= 1 && it.all(Char::isDigit)) newPoints = it },
            label = { Text("업무량 점수 (1~5)") }, enabled = editing, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val points = newPoints.toIntOrNull()
        Button(
            enabled = enabled && !busy && newName.isNotBlank() && newCategory.isNotBlank() && points != null && points in 1..5,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val validPoints = points ?: return@Button
                val id = creationId
                val name = newName.trim()
                val category = newCategory.trim()
                creationStarted = true
                execute {
                    gateway.createChore(householdId, id, name, category, "", validPoints, accountUid)
                    "서버 응답을 받았습니다. 등록된 집안일을 확인하고 있습니다."
                }
            }
        ) { Text(if (creationStarted) "동일 등록 요청 재시도" else "공동 집안일 등록") }
        if (creationStarted) {
            Text("등록 결과가 확인될 때까지 같은 요청 ID를 사용합니다.", color = palette.muted,
                style = MaterialTheme.typography.bodySmall)
        }
        Text("등록된 집안일 및 반복 설정", style = MaterialTheme.typography.titleMedium)
        if (snapshot.chores.isEmpty()) Text("등록된 공동 집안일이 없습니다.")
        snapshot.chores.sortedWith(compareBy({ it.name }, { it.id })).forEach { chore ->
            TextButton(
                enabled = !busy,
                onClick = { selectedId = chore.id },
                modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
            ) { Text(chore.name + if (selectedId == chore.id) " · 선택" else "") }
        }
        val selected = snapshot.chores.firstOrNull { it.id == selectedId }
        if (selected != null) {
            key(selected.id, selected.revision) {
                val initial = selected.schedule ?: RemoteSchedule(
                    startEpochDay = sharedChoreToday().toEpochDay(),
                    repeatMode = "NONE",
                    assignmentMode = "FIXED",
                    memberUids = listOf(selected.assigneeUid.takeIf { uid -> snapshot.members.any { it.uid == uid } } ?: accountUid)
                )
                var start by rememberSaveable { mutableStateOf(LocalDate.ofEpochDay(initial.startEpochDay).toString()) }
                var repeat by rememberSaveable { mutableStateOf(initial.repeatMode) }
                var weekdays by remember { mutableStateOf(initial.weekdays.toSet()) }
                var interval by rememberSaveable { mutableStateOf(initial.intervalDays.toString()) }
                var assignment by rememberSaveable { mutableStateOf(initial.assignmentMode) }
                var participants by remember { mutableStateOf(initial.memberUids) }
                val canEdit = enabled && !busy && !creationStarted
                Text(selected.name + " 반복 규칙", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = start, onValueChange = { if (it.length <= 10) start = it },
                    label = { Text("시작일 (YYYY-MM-DD)") }, singleLine = true, enabled = canEdit,
                    modifier = Modifier.fillMaxWidth()
                )
                ChoreRepeatMode.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                        pair.forEach { mode ->
                            TextButton(enabled = canEdit, onClick = { repeat = mode.name }) {
                                Text(mode.label + if (repeat == mode.name) " · 선택" else "")
                            }
                        }
                    }
                }
                if (repeat == "WEEKLY" || repeat == "BIWEEKLY") {
                    choreWeekdays.chunked(3).forEach { row ->
                        Row {
                            row.forEach { weekday ->
                                TextButton(enabled = canEdit, onClick = {
                                    weekdays = if (weekday.value in weekdays) weekdays - weekday.value else weekdays + weekday.value
                                }) { Text(choreDayName(weekday) + if (weekday.value in weekdays) " ✓" else "") }
                            }
                        }
                    }
                }
                if (repeat == "CUSTOM") {
                    OutlinedTextField(
                        value = interval, onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) interval = it },
                        label = { Text("반복 간격 (1~30일)") }, enabled = canEdit, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("담당 방식", style = MaterialTheme.typography.titleMedium)
                ChoreAssignmentMode.entries.forEach { mode ->
                    TextButton(enabled = canEdit, onClick = {
                        assignment = mode.name
                        if (assignment == "FIXED") participants = participants.take(1)
                    }) { Text(mode.label + if (assignment == mode.name) " · 선택" else "") }
                }
                Text("담당 구성원을 선택해 주세요. 표시명 연동 전까지 구성원 번호로 구분합니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                snapshot.members.forEachIndexed { index, person ->
                    val label = if (person.isOwner) "소유자" else "구성원 $index"
                    val position = participants.indexOf(person.uid)
                    TextButton(enabled = canEdit, onClick = {
                        participants = if (assignment == "FIXED") listOf(person.uid)
                        else if (person.uid in participants) participants - person.uid
                        else participants + person.uid
                    }) {
                        Text(label + (if (person.uid == accountUid) " (나)" else "") +
                            if (position >= 0) " · ${position + 1}순위" else "")
                    }
                }
                val candidate = runCatching {
                    RemoteSchedule(
                        startEpochDay = LocalDate.parse(start).toEpochDay(),
                        repeatMode = repeat,
                        weekdays = if (repeat in setOf("WEEKLY", "BIWEEKLY")) weekdays.sorted() else emptyList(),
                        intervalDays = if (repeat == "CUSTOM") requireNotNull(interval.toIntOrNull()) else 2,
                        assignmentMode = assignment,
                        memberUids = participants
                    ).also { rule -> require(rule.memberUids.all { uid -> snapshot.members.any { it.uid == uid } }) }
                }.getOrNull()
                Button(
                    enabled = canEdit && candidate != null && candidate != selected.schedule,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val rule = candidate ?: return@Button
                        execute {
                            gateway.configureSchedule(householdId, selected.id, selected.revision, rule)
                            "반복 설정 응답을 받았습니다. 최신 규칙을 확인해 주세요."
                        }
                    }
                ) { Text("반복 설정 저장") }
                Button(
                    enabled = canEdit && selected.schedule != null && candidate == selected.schedule,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val today = sharedChoreToday().toEpochDay()
                        execute {
                            val result = gateway.generateSchedule(householdId, selected.id, selected.revision, today, today + 62)
                            "서버 응답: 새 회차 ${result.created}개 · 기존 회차 ${result.existing}개 유지"
                        }
                    }
                ) { Text("저장된 규칙으로 63일 일정 생성") }
                Text("기존 회차와 완료 기록은 수정하지 않습니다. 균형 배정은 같은 달력 주의 예정 업무량을 비교합니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        if (busy) Text("서버 요청 중…")
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted) }
    }
}
