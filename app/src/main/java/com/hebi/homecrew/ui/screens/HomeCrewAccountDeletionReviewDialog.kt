package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.AccountDeletionPreflight
import com.hebi.homecrew.data.cloud.HomeCrewSharedHouseholdGateway
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Composable
internal fun HomeCrewAccountDeletionReviewDialog(gateway: HomeCrewSharedHouseholdGateway?, onClose: () -> Unit) {
    val palette = LocalHomeCrewColors.current
    var plan by remember(gateway) { mutableStateOf<AccountDeletionPreflight?>(null) }
    var loading by remember(gateway) { mutableStateOf(false) }
    var failed by remember(gateway) { mutableStateOf(false) }
    var attempt by remember(gateway) { mutableStateOf(0) }
    LaunchedEffect(gateway, attempt) {
        plan = null
        failed = false
        if (gateway != null) {
            loading = true
            try { plan = withTimeout(35_000) { gateway.deletionPreflight() } }
            catch (_: TimeoutCancellationException) { failed = true }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    HomeCrewManagementPage(
        onDismissRequest = onClose,
        title = { Text("계정 삭제 사전 확인") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                BorderedSection {
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                        Text("아직 삭제하지 않았습니다", style = MaterialTheme.typography.titleMedium, color = palette.warning)
                        Text("공동 공간의 접근 권한과 기록을 확인합니다. 개발 미리보기에서는 인증 계정만 삭제할 수 없습니다. 이 화면에서 데이터나 계정은 삭제하지 않습니다.")
                    }
                }
                if (gateway == null) Text("공동 공간 설정을 확인하지 못했습니다.", color = palette.error)
                if (loading) Text("서버에서 계정 자료를 확인 중…")
                if (failed) Text("자료 범위를 확인하지 못했습니다. 계정 삭제는 차단됩니다.", color = palette.error)
                plan?.let { result ->
                    Text("공동 공간", style = MaterialTheme.typography.titleMedium)
                    Text("소유 ${result.ownerCount}개 · 참여 ${result.memberCount}개")
                    result.spaces.forEach { space ->
                        Text(space.name + if (space.role == "OWNER") " · 소유자" else " · 구성원")
                    }
                    if (result.ownerCount > 0) Text("소유권 이전 또는 공간 정리 정책 확인이 필요합니다.")
                    if (result.memberCount > 0) Text("참여 공간의 탈퇴 및 과거 기록 처리 범위를 확인해야 합니다.")
                    if (result.indexIssues > 0) Text("일치하지 않는 공간 인덱스 ${result.indexIssues}개", color = palette.warning)
                    if (result.lockedSpaces > 0) Text("이전 반영 잠금 ${result.lockedSpaces}개", color = palette.warning)
                    Text("발견한 연관 자료", style = MaterialTheme.typography.titleMedium)
                    if (result.dataCategories.isEmpty()) Text("검사한 범위에서 추가 참조를 발견하지 못했습니다.")
                    result.dataCategories.forEach { Text(it) }
                    Text("이 검사는 전체 삭제 완료를 보장하지 않습니다. 임시 자료, 기록 익명화, 서버 보존 정책 확인이 필요합니다.",
                        style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
                Text("기기에 저장한 집안일과 외부 백업은 이 작업에서 변경하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                if (gateway != null) TextButton(enabled = !loading, onClick = { attempt++ }) { Text("서버 상태 다시 확인") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
}
