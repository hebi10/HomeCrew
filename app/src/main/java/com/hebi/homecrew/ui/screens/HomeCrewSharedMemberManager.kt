package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.HomeCrewSharedHouseholdGateway
import com.hebi.homecrew.data.cloud.RemoteHouseholdMember
import com.hebi.homecrew.data.cloud.SharedHouseholdRoster
import com.hebi.homecrew.data.cloud.SharedMembershipAction
import com.hebi.homecrew.data.cloud.normalizeSharedMemberName
import com.hebi.homecrew.data.cloud.sharedMemberLabel
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private data class MemberConfirmation(val action: SharedMembershipAction, val member: RemoteHouseholdMember,
    val label: String, val digest: String)
private data class NameEdit(val member: RemoteHouseholdMember, val digest: String)

@Composable
internal fun HomeCrewSharedMemberManager(
    gateway: HomeCrewSharedHouseholdGateway,
    householdId: String,
    householdName: String,
    accountUid: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val palette = LocalHomeCrewColors.current
    var roster by remember(gateway, householdId, accountUid) { mutableStateOf<SharedHouseholdRoster?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<NameEdit?>(null) }
    var name by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<MemberConfirmation?>(null) }

    suspend fun refresh() {
        roster = null
        roster = withTimeout(35_000) { gateway.roster(householdId) }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { action() }
            catch (_: TimeoutCancellationException) {
                roster = null
                message = "서버 응답을 확인하지 못했습니다. 다시 확인해 주세요."
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                roster = null
                message = "구성원 상태가 달라졌거나 요청이 거부되었습니다. 다시 확인해 주세요."
            } finally { busy = false }
        }
    }
    LaunchedEffect(gateway, householdId, accountUid) { run { refresh() } }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("구성원 관리") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                Text(householdName, style = MaterialTheme.typography.titleMedium)
                TextButton(enabled = !busy, onClick = { run { refresh() } }) { Text("구성원 다시 확인") }
                roster?.let { current ->
                    val self = current.members.firstOrNull { it.uid == accountUid }
                    val owner = self?.isOwner == true
                    Text("구성원 ${current.members.size}/8명")
                    if (current.transferBlocked && owner) {
                        Text("데이터 이전 세션 정리 전에는 소유권을 이전할 수 없습니다.", color = palette.warning,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    current.members.forEachIndexed { index, person ->
                        val label = sharedMemberLabel(person, index, accountUid)
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(if (person.isOwner) "소유자" else "일반 구성원", style = MaterialTheme.typography.bodySmall)
                        if (owner || person.uid == accountUid) {
                            TextButton(enabled = !busy, onClick = {
                                edit = NameEdit(person, current.digest)
                                name = person.displayName.orEmpty()
                            }) { Text("이름 수정") }
                        }
                        if (owner && !person.isOwner) {
                            TextButton(enabled = !busy && !current.transferBlocked, onClick = {
                                confirmation = MemberConfirmation(SharedMembershipAction.TRANSFER, person, label, current.digest)
                            }) { Text("소유권 이전") }
                            TextButton(enabled = !busy, onClick = {
                                confirmation = MemberConfirmation(SharedMembershipAction.REMOVE, person, label, current.digest)
                            }) { Text("구성원 제거") }
                        }
                    }
                    if (self != null && !owner) {
                        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            confirmation = MemberConfirmation(SharedMembershipAction.LEAVE, self, "내 계정", current.digest)
                        }) { Text("공동 공간 탈퇴") }
                    }
                    Text("탈퇴·제거 후 과거 배정과 완료 기록은 유지합니다. 남은 반복 일정의 담당자는 다시 설정해야 합니다.",
                        style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
                if (busy) Text("서버 요청 중…")
                message?.let { Text(it, color = palette.warning, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text("닫기") } }
    )
    edit?.let { target ->
        val normalized = runCatching { normalizeSharedMemberName(name) }.getOrNull()
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("구성원 이름 수정") },
            text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 12) name = it },
                singleLine = true, label = { Text("표시 이름 (12자 이내)") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = normalized != null && !busy, onClick = {
                    val value = normalized ?: return@TextButton
                    edit = null
                    run {
                        withTimeout(35_000) { gateway.renameMember(householdId, target.member.uid, value, target.digest) }
                        refresh()
                        message = "최신 구성원 이름을 확인했습니다."
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("취소") } }
        )
    }
    confirmation?.let { request ->
        val title = when (request.action) {
            SharedMembershipAction.TRANSFER -> "소유권 이전"
            SharedMembershipAction.LEAVE -> "공동 공간 탈퇴"
            SharedMembershipAction.REMOVE -> "구성원 제거"
        }
        val description = when (request.action) {
            SharedMembershipAction.TRANSFER -> "${request.label}에게 관리 권한을 이전합니다. 본인은 일반 구성원으로 남으며 이전에 발급한 초대 코드는 무효화됩니다."
            SharedMembershipAction.LEAVE -> "공동 공간의 조회·수정 권한이 없어집니다. 과거 기록과 기기 원본은 유지됩니다."
            SharedMembershipAction.REMOVE -> "${request.label}의 조회·수정 권한을 제거합니다. 과거 기록은 보존하며 담당자를 다시 선택해야 합니다."
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(title) },
            text = { Text(description) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmation = null
                    run {
                        withTimeout(35_000) { gateway.changeMembership(householdId, request.member.uid, request.action, request.digest) }
                        if (request.action == SharedMembershipAction.LEAVE) onClose()
                        else {
                            refresh()
                            message = "최신 구성원 상태를 확인했습니다."
                        }
                    }
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } }
        )
    }
}
