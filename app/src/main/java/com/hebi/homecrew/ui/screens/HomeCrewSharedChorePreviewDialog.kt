package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.RemoteHouseholdReadGateway
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

/**
 * Read-only shared-household preview. The account/house selection is supplied by
 * HC-02 after authorization; the local Room screens and records are not modified.
 */
@Composable
internal fun HomeCrewSharedChorePreviewDialog(
    gateway: RemoteHouseholdReadGateway,
    householdId: String,
    householdName: String,
    accountUid: String,
    onClose: () -> Unit
) {
    key(gateway, accountUid, householdId) {
        val palette = LocalHomeCrewColors.current
        var day by rememberSaveable { mutableLongStateOf(sharedChoreToday().toEpochDay()) }
        var retry by remember { mutableIntStateOf(0) }
        val date = LocalDate.ofEpochDay(day)

        key(retry) {
            val stream = remember(gateway, householdId) { observeSharedChorePreview(gateway, householdId) }
            val state by stream.collectAsState(initial = SharedChorePreviewState.Loading)
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(if (state is SharedChorePreviewState.Ready) householdName else "공동 집안일") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
                    ) {
                        Text(
                            "공동 집안일 미리보기 · 조회 전용",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted
                        )
                        Text(
                            "이 기기에 저장한 집안일은 변경되지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted
                        )
                        when (val current = state) {
                            SharedChorePreviewState.Loading -> Text("서버에서 집안일을 확인하고 있습니다…")
                            SharedChorePreviewState.Offline -> {
                                Text("연결을 확인하고 있습니다. 서버에서 확인되지 않은 집안일은 표시하지 않습니다.")
                                TextButton(onClick = { retry++ }) { Text("다시 연결") }
                            }
                            SharedChorePreviewState.Unavailable -> {
                                Text("공동 공간의 정보를 확인하지 못했습니다. 연결 및 참여 권한을 확인해 주세요.")
                                TextButton(onClick = { retry++ }) { Text("다시 시도") }
                            }
                            is SharedChorePreviewState.Ready -> {
                                Text("실시간 연결", color = palette.success, style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { day = date.minusDays(1).toEpochDay() }) { Text("이전") }
                                    TextButton(onClick = { day = sharedChoreToday().toEpochDay() }) { Text("오늘") }
                                    TextButton(onClick = { day = date.plusDays(1).toEpochDay() }) { Text("다음") }
                                }
                                Text(date.toString() + " (한국 시간)", style = MaterialTheme.typography.titleMedium)
                                val daily = sharedChoreDay(current.snapshot, date, accountUid)
                                if (daily.rows.isEmpty() && daily.missingDefinitionCount == 0) {
                                    Text("이날 예정된 공동 집안일이 없습니다.")
                                }
                                daily.rows.forEach { row ->
                                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                                        val label = when (row.status) {
                                            SharedChoreStatus.PENDING -> "미완료"
                                            SharedChoreStatus.COMPLETED -> "완료"
                                            SharedChoreStatus.SKIPPED -> "건너뜀"
                                        }
                                        val owner = if (row.assignedToMe) "내 담당" else "다른 구성원 담당"
                                        Text(
                                            listOf(row.category, owner, "난이도 ${row.points}", label)
                                                .filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.muted
                                        )
                                    }
                                }
                                if (daily.missingDefinitionCount > 0) {
                                    Text(
                                        "일부 집안일 정보가 아직 일치하지 않습니다. 동기화 후 다시 확인해 주세요.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.warning
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onClose) { Text("닫기") }
                }
            )
        }
    }
}
