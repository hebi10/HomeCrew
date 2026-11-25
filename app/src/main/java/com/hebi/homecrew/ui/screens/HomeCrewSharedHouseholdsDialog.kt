package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private enum class SharedSpacePage { LIST, DETAIL, CREATE, INVITE }

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
    var selectedMigrationHome by remember(gateway, accountUid) { mutableStateOf<SharedHouseholdSummary?>(null) }
    var selectedMemberHome by remember(gateway, accountUid) { mutableStateOf<SharedHouseholdSummary?>(null) }
    var page by remember(gateway, accountUid) { mutableStateOf(SharedSpacePage.LIST) }
    var detailId by remember(gateway, accountUid) { mutableStateOf<String?>(null) }
    val detail = households.firstOrNull { it.id == detailId }
    fun openPage(next: SharedSpacePage) {
        if (busy) return
        invitation = null
        message = null
        if (next != SharedSpacePage.INVITE) joinCode = ""
        page = next
    }
    fun goBack() {
        if (busy) return
        when (page) {
            SharedSpacePage.LIST -> onClose()
            SharedSpacePage.DETAIL, SharedSpacePage.CREATE -> {
                detailId = null
                openPage(SharedSpacePage.LIST)
            }
            SharedSpacePage.INVITE -> {
                val next = if (detailId != null) SharedSpacePage.DETAIL else SharedSpacePage.LIST
                openPage(next)
            }
        }
    }

    suspend fun refresh() {
        loaded = false
        households = emptyList()
        invitation = null
        households = gateway.list()
        loaded = true
        if (detailId != null && households.none { it.id == detailId }) {
            detailId = null
            page = SharedSpacePage.LIST
        }
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

    val memberHome = selectedMemberHome
    if (memberHome != null) {
        HomeCrewSharedMemberManager(
            gateway = gateway, householdId = memberHome.id, householdName = memberHome.name,
            accountUid = accountUid,
            onClose = { selectedMemberHome = null; runAction { refresh() } }
        )
        return
    }

    val migrationHome = selectedMigrationHome
    if (migrationHome != null) {
        val choreGateway = remember(gateway, accountUid) { gateway.choreGateway() }
        val journal = remember(gateway, accountUid, migrationHome.id) { gateway.migrationJournal(migrationHome.id) }
        HomeCrewStagedMigrationManager(
            gateway = choreGateway, journal = journal, householdId = migrationHome.id,
            householdName = migrationHome.name,
            onClose = { selectedMigrationHome = null; runAction { refresh() } }
        )
        return
    }

    val currentHome = selectedHome
    if (currentHome != null) {
        val choreGateway = remember(gateway, accountUid) { gateway.choreGateway() }
        val journal = remember(gateway, accountUid, currentHome.id) {
            gateway.completionJournal(currentHome.id)
        }
        val migrationJournal = remember(gateway, accountUid, currentHome.id) {
            gateway.migrationJournal(currentHome.id)
        }
        HomeCrewSharedChorePreviewDialog(
            gateway = choreGateway,
            journal = journal,
            migrationJournal = migrationJournal,
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
    val title = when (page) {
        SharedSpacePage.LIST -> "공동 공간 관리"
        SharedSpacePage.DETAIL -> "공동 공간 설정"
        SharedSpacePage.CREATE -> "새 공동 공간"
        SharedSpacePage.INVITE -> "공동 공간 초대·참여"
    }
    HomeCrewManagementPage(
        onDismissRequest = ::goBack,
        title = { Text(title) },
        text = {
            key(page, detailId) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)
                ) {
                    if (busy) Text("서버 요청 중…", style = MaterialTheme.typography.bodySmall)
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    when (page) {
                        SharedSpacePage.LIST -> {
                            Text("개발 미리보기입니다. 현재 기기의 집안일과 완료 기록은 자동으로 이전하거나 공유하지 않습니다.",
                                style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("내 공동 공간", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { runAction { refresh() } }, enabled = !busy) { Text("새로고침") }
                            }
                            if (loaded && households.isEmpty()) Text("참여 중인 공동 공간이 없습니다.")
                            if (!loaded && !busy) Text("공동 공간을 확인하려면 새로고침해 주세요.")
                            households.forEach { home ->
                                BorderedSection {
                                    TextButton(
                                        enabled = loaded && !busy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)
                                            .semantics { contentDescription = "공동 공간: ${home.name}" },
                                        onClick = { detailId = home.id; openPage(SharedSpacePage.DETAIL) }
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(home.name, style = MaterialTheme.typography.titleMedium)
                                                Text("${if (home.isOwner) "소유자" else "구성원"} · ${home.memberCount}/8명",
                                                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                                            }
                                            Text("›")
                                        }
                                    }
                                }
                            }
                            Button(enabled = loaded && !busy, onClick = { openPage(SharedSpacePage.CREATE) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                Text("공동 공간 만들기")
                            }
                            TextButton(enabled = loaded && !busy, onClick = { detailId = null; openPage(SharedSpacePage.INVITE) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                Text("초대 코드로 참여하기")
                            }
                        }
                        SharedSpacePage.DETAIL -> {
                            if (detail == null || !loaded) {
                                Text("공동 공간 정보를 확인해 주세요.")
                                TextButton(enabled = !busy, onClick = { runAction { refresh() } }) { Text("새로고침") }
                            } else {
                                Text(detail.name, style = MaterialTheme.typography.titleLarge)
                                Text("${if (detail.isOwner) "소유자" else "구성원"} · ${detail.memberCount}/8명",
                                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                                BorderedSection {
                                    TextButton(enabled = !busy, onClick = { selectedHome = detail },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                        Text("공동 집안일 보기")
                                    }
                                    TextButton(enabled = !busy, onClick = { selectedMemberHome = detail },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                        Text("구성원 관리")
                                    }
                                    if (detail.isOwner) {
                                        TextButton(enabled = !busy, onClick = { selectedMigrationHome = detail },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                            Text("대용량 이전 관리")
                                        }
                                        TextButton(enabled = !busy && detail.memberCount < 8,
                                            onClick = { openPage(SharedSpacePage.INVITE) },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                            Text("가족 초대")
                                        }
                                        if (detail.memberCount >= 8) Text("공동 공간은 최대 8명까지 참여할 수 있습니다.",
                                            style = MaterialTheme.typography.bodySmall, color = palette.muted)
                                    }
                                }
                            }
                        }
                        SharedSpacePage.CREATE -> {
                            Text("기기 내 자료는 자동으로 이전하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                            OutlinedTextField(value = name, onValueChange = { value ->
                                if (value.length <= 80) {
                                    if (value.trim() != name.trim()) requestId = UUID.randomUUID().toString()
                                    name = value
                                }
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                                label = { Text("공동 공간 이름") }, singleLine = true)
                            Button(enabled = !busy && loaded && validName != null, onClick = {
                                val request = sharedHouseholdCreateRequest(name, requestId)
                                runAction {
                                    gateway.create(request)
                                    name = ""
                                    requestId = UUID.randomUUID().toString()
                                    refresh()
                                    page = SharedSpacePage.LIST
                                    message = "공동 공간을 만들었습니다."
                                }
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                Text("공동 공간 만들기")
                            }
                            if (!loaded && !busy) TextButton(onClick = { runAction { refresh() } }) { Text("새로고침") }
                        }
                        SharedSpacePage.INVITE -> {
                            if (loaded && detail?.isOwner == true && detail.memberCount < 8) {
                                Text(detail.name, style = MaterialTheme.typography.titleMedium)
                                Text("초대 코드는 1회 사용 후 만료되며 유효기간이 있습니다.",
                                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
                                Button(enabled = !busy, onClick = {
                                    invitation = null
                                    runAction { invitation = gateway.invite(detail.id) }
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                    Text("가족 초대 코드 발급")
                                }
                                invitation?.takeIf { it.householdId == detail.id }?.let { invite ->
                                    BorderedSection {
                                        SelectionContainer { Text(invite.inviteCode, style = MaterialTheme.typography.titleLarge) }
                                    }
                                    val expires = Instant.ofEpochMilli(invite.expiresAtMs)
                                        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                                    Text("1회 사용 · 만료 $expires", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("초대 코드로 참여하기", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(value = joinCode, onValueChange = { if (it.length <= 30) joinCode = it },
                                enabled = !busy, label = { Text("초대 코드") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(enabled = !busy && loaded && validCode != null, onClick = {
                                val code = validCode ?: return@Button
                                runAction {
                                    gateway.join(code)
                                    joinCode = ""
                                    detailId = null
                                    refresh()
                                    page = SharedSpacePage.LIST
                                    message = "공동 공간에 참여했습니다."
                                }
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)) {
                                Text("초대 수락")
                            }
                            if (!loaded && !busy) TextButton(onClick = { runAction { refresh() } }) { Text("새로고침") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::goBack, enabled = !busy) {
            Text(if (page == SharedSpacePage.LIST) "닫기" else "뒤로")
        } }
    )
}
