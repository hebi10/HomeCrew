package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.cloud.HomeCrewCloudInvite
import com.hebi.homecrew.data.cloud.HomeCrewSharedHouseholdGateway
import com.hebi.homecrew.data.cloud.SharedHouseholdSummary
import com.hebi.homecrew.data.cloud.normalizeCloudHouseholdName
import com.hebi.homecrew.data.cloud.normalizeHomeCrewInviteCode
import com.hebi.homecrew.data.cloud.sharedHouseholdCreateRequest
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HomeCrewSharedHouseholdsDialog(
    gateway: HomeCrewSharedHouseholdGateway,
    accountUid: String,
    onClose: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    val scope = rememberCoroutineScope()
    var households by remember { mutableStateOf<List<SharedHouseholdSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var joinCode by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf<HomeCrewCloudInvite?>(null) }
    var selectedHome by remember(gateway, accountUid) { mutableStateOf<SharedHouseholdSummary?>(null) }
    val choreGateway = remember(gateway, accountUid) { gateway.choreGateway() }

    suspend fun refresh() {
        loaded = false
        households = emptyList()
        invitation = null
        households = gateway.list()
        loaded = true
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "요청 결과를 확인하지 못했습니다. 연결 상태를 확인하고 새로고침해 주세요."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(gateway) { runAction { refresh() } }

    val currentHome = selectedHome
    if (currentHome != null) {
        HomeCrewSharedChorePreviewDialog(
            gateway = choreGateway,
            householdId = currentHome.id,
            householdName = currentHome.name,
            accountUid = accountUid,
            onClose = {
                selectedHome = null
                runAction { refresh() }
            }
        )
        return
    }

    val validName = runCatching { normalizeCloudHouseholdName(name) }.getOrNull()
    val validCode = runCatching { normalizeHomeCrewInviteCode(joinCode) }.getOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("공동 공간 관리") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
            ) {
                Text(
                    "개발 미리보기입니다. 현재 기기의 집안일과 완료 기록은 자동으로 이전하거나 공유하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted
                )
                Text("내 공동 공간", style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { runAction { refresh() } },
                    enabled = !busy
                ) { Text("새로고침") }
                if (busy) Text("서버 요청 중…")
                if (loaded && households.isEmpty()) Text("참여 중인 공동 공간이 없습니다.")
                if (!loaded && !busy) Text("공동 공간을 확인하려면 새로고침해 주세요.")
                households.forEach { home ->
                    Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                        Text(home.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${if (home.isOwner) "소유자" else "구성원"} · ${home.memberCount}/8명",
                            style = MaterialTheme.typography.bodySmall, color = palette.muted
                        )
                        TextButton(
                            enabled = loaded && !busy,
                            onClick = {
                                invitation = null
                                selectedHome = home
                            },
                            modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
                        ) { Text("공동 집안일 보기") }
                        if (home.isOwner && home.memberCount < 8) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    invitation = null
                                    runAction { invitation = gateway.invite(home.id) }
                                },
                                modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
                            ) { Text("가족 초대 코드 발급") }
                        }
                        invitation?.takeIf { it.householdId == home.id }?.let { invite ->
                            SelectionContainer { Text(invite.inviteCode) }
                            val expires = Instant.ofEpochMilli(invite.expiresAtMs)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                            Text("1회 사용 · 만료 $expires", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("새 공동 공간", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        if (value.length <= 80) {
                            if (value.trim() != name.trim()) requestId = UUID.randomUUID().toString()
                            name = value
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("공동 공간 이름") },
                    singleLine = true
                )
                Button(
                    enabled = !busy && loaded && validName != null,
                    onClick = {
                        val request = sharedHouseholdCreateRequest(name, requestId)
                        runAction {
                            gateway.create(request)
                            name = ""
                            requestId = UUID.randomUUID().toString()
                            refresh()
                            message = "공동 공간을 만들었습니다."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("공동 공간 만들기") }
                Text("가족 초대 수락", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { if (it.length <= 30) joinCode = it },
                    enabled = !busy,
                    label = { Text("초대 코드") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    enabled = !busy && loaded && validCode != null,
                    onClick = {
                        val code = validCode ?: return@Button
                        runAction {
                            gateway.join(code)
                            joinCode = ""
                            refresh()
                            message = "공동 공간에 참여했습니다."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("초대 수락") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text("닫기") } }
    )
}
