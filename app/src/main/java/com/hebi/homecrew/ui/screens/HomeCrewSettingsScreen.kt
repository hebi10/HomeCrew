package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.reminder.HomeCrewReminderPreferences
import com.hebi.homecrew.reminder.HomeCrewReminderScheduler
import com.hebi.homecrew.reminder.HomeCrewReminderTime
import com.hebi.homecrew.data.auth.homeCrewGoogleSignIn
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.houseNameError
import com.hebi.homecrew.ui.setup.memberNameError
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode

private enum class SettingsSection(val title: String) {
    ROOT("설정"), HOUSE("집 설정"), MEMBERS("구성원 관리"), MEMBER_EDIT("구성원 이름 수정"),
    ACCOUNT("계정 관리"), THEME("화면 테마"), REMINDER("알림 설정"), PRIVACY("개인정보")
}

@Composable
private fun SettingsMenuRow(
    title: String, subtitle: String, icon: ImageVector, tag: String,
    onClick: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    BorderedSection(Modifier.heightIn(min = 64.dp)
        .testTag(tag)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "설정 메뉴: $title" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space4)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.muted)
        }
    }
}

@Composable
fun HomeCrewSettingsScreen(
    household: HouseholdSetup,
    assignedMembers: Set<String>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSave: (HouseholdSetup) -> Boolean,
    onBack: () -> Unit,
    onMemberChange: suspend (String, String?) -> String? = { _, _ -> "구성원 변경을 지원하지 않는 화면입니다." },
    onOpenDataManagement: (() -> Unit)? = null
) {
    val palette = LocalHomeCrewColors.current
    val context = LocalContext.current
    var privacyLinkError by remember { mutableStateOf(false) }
    val reminderPrefs = remember(context) { HomeCrewReminderPreferences(context) }
    var reminderSettings by remember { mutableStateOf(reminderPrefs.read()) }
    var reminderTimeText by remember { mutableStateOf(reminderSettings.time.toString()) }
    var reminderNotice by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminderSettings = reminderPrefs.updateEnabled(true)
            HomeCrewReminderScheduler.apply(context, reschedule = true)
            reminderNotice = "오늘 할 일 알림을 설정했습니다."
        } else {
            reminderNotice = "알림 권한이 허용되지 않아 알림을 켜지 않았습니다."
        }
    }
    var homeName by rememberSaveable(household.homeName) { mutableStateOf(household.homeName) }
    var newMember by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(SettingsSection.ROOT) }
    var editMember by rememberSaveable { mutableStateOf<String?>(null) }
    var replacement by rememberSaveable { mutableStateOf("") }
    var deleteMember by remember { mutableStateOf<String?>(null) }
    var memberBusy by remember { mutableStateOf(false) }
    var accountBusy by remember { mutableStateOf(false) }

    fun navigate(next: SettingsSection) {
        if (memberBusy || accountBusy) return
        error = null
        notice = null
        section = next
    }

    fun navigateBack() {
        if (memberBusy || accountBusy) return
        when (section) {
            SettingsSection.ROOT -> onBack()
            SettingsSection.MEMBER_EDIT -> { editMember = null; navigate(SettingsSection.MEMBERS) }
            else -> navigate(SettingsSection.ROOT)
        }
    }
    BackHandler(enabled = memberBusy || accountBusy || section != SettingsSection.ROOT) { navigateBack() }

    fun changeMember(member: String, newName: String?) {
        if (memberBusy) return
        memberBusy = true
        error = null
        notice = null
        scope.launch {
            try {
                val failure = onMemberChange(member, newName)
                if (failure != null) error = failure
                else {
                    notice = if (newName == null) "구성원을 삭제했습니다." else "구성원 이름과 연결된 기록을 수정했습니다."
                    if (newName != null && section == SettingsSection.MEMBER_EDIT) {
                        editMember = null
                        section = SettingsSection.MEMBERS
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "구성원 변경 상태를 확인하지 못했습니다. 다시 확인해 주세요."
            } finally { memberBusy = false }
        }
    }

    fun persist(updated: HouseholdSetup): Boolean {
        if (memberBusy) return false
        val saved = onSave(updated)
        if (saved) {
            error = null
            notice = "기기에 저장했습니다."
        } else {
            error = "설정을 저장하지 못했습니다. 다시 시도해 주세요."
            notice = null
        }
        return saved
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.background,
        contentColor = palette.ink
    ) {
        key(section) {
        LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .testTag("homecrew-settings-list"),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space16
        ),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space16)
    ) {
        item {
            ScreenHeader(section.title, if (section == SettingsSection.ROOT) "이 기기의 집안일과 구성원을 관리합니다." else null) {
                TextButton(onClick = ::navigateBack, enabled = !memberBusy && !accountBusy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("homecrew-settings-back")) { Text("뒤로") }
            }
        }
        if (section == SettingsSection.ROOT) {
            item {
                SectionHeading("우리 집")
                Text("기기 내 저장 · ${household.members.size}명", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            item { SettingsMenuRow("집 설정", household.homeName, Icons.Outlined.Home, "homecrew-settings-household") { navigate(SettingsSection.HOUSE) } }
            item { SettingsMenuRow("구성원 관리", "${household.members.size}명 · 기본 ${household.defaultMember}", Icons.Outlined.People,
                "homecrew-settings-members") { navigate(SettingsSection.MEMBERS) } }
            item { SectionHeading("앱 설정") }
            item { SettingsMenuRow("계정", if (homeCrewGoogleSignIn == null) "로그인 없이 사용" else "로그인 및 공동 공간 관리",
                Icons.Outlined.AccountCircle, "homecrew-settings-account") { navigate(SettingsSection.ACCOUNT) } }
            item { SettingsMenuRow("화면 테마", themeMode.label, Icons.Outlined.DarkMode, "homecrew-settings-theme") { navigate(SettingsSection.THEME) } }
            item { SettingsMenuRow("알림 설정", if (reminderSettings.enabled) reminderSettings.time.toString() else "사용 안 함",
                Icons.Outlined.Notifications, "homecrew-settings-reminder") { navigate(SettingsSection.REMINDER) } }
            if (onOpenDataManagement != null) item {
                SettingsMenuRow("데이터 관리", "로컬 백업 및 복원", Icons.Outlined.Folder, "homecrew-settings-data", onOpenDataManagement)
            }
            item { SectionHeading("정보") }
            item { SettingsMenuRow("개인정보처리방침", "데이터 보관 및 개인정보 안내", Icons.Outlined.Info,
                "homecrew-settings-privacy") { navigate(SettingsSection.PRIVACY) } }
        }
        if (section == SettingsSection.HOUSE) item {
            Text("이 기기의 공간 이름", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = homeName,
                onValueChange = { homeName = it; error = null; notice = null },
                enabled = !memberBusy,
                label = { Text("집 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("homecrew-home-name")
            )
            Button(
                onClick = {
                    val validation = houseNameError(homeName)
                    if (validation != null) error = validation
                    else persist(household.copy(homeName = homeName.trim()))
                },
                enabled = !memberBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("homecrew-save-home")
            ) { Text("집 이름 저장") }
        }
        if (section == SettingsSection.MEMBERS) item {
            SectionHeading("등록된 구성원", "${household.members.size}명")
            Text("이 기기에서 사용하는 구성원입니다.", color = palette.muted)
            household.members.forEach { member ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(member, modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        TextButton(
                            onClick = { persist(household.copy(defaultMember = member)) },
                            enabled = !memberBusy && household.defaultMember != member,
                            modifier = Modifier.heightIn(min = 48.dp)
                                .testTag("homecrew-default-member-$member")
                        ) {
                            Text(if (household.defaultMember == member) "기본 구성원" else "기본으로 설정")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                        TextButton(enabled = !memberBusy, onClick = {
                            editMember = member
                            replacement = member
                            navigate(SettingsSection.MEMBER_EDIT)
                        }, modifier = Modifier.testTag("homecrew-rename-member-$member")) { Text("이름 수정") }
                        TextButton(enabled = !memberBusy && household.members.size > 1 && member !in assignedMembers,
                            onClick = { deleteMember = member },
                            modifier = Modifier.testTag("homecrew-delete-member-$member")) { Text("삭제") }
                    }
                    if (member in assignedMembers) Text("연결된 기록이 있어 삭제할 수 없습니다.",
                        style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
            OutlinedTextField(
                value = newMember,
                onValueChange = { newMember = it; error = null; notice = null },
                enabled = !memberBusy,
                label = { Text("새 구성원 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .testTag("homecrew-member-input")
                    .semantics { contentDescription = "새 구성원 이름 입력" }
            )
            Button(
                onClick = {
                    val validation = memberNameError(newMember, household.members)
                    if (validation != null) error = validation
                    else if (persist(household.copy(members = household.members + newMember.trim()))) {
                        newMember = ""
                    }
                },
                enabled = !memberBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("homecrew-member-add")
                    .semantics { contentDescription = "구성원 추가 버튼" }
            ) { Text("구성원 추가") }
            Text(
                "이름 수정은 배정·완료 기록에도 적용됩니다. 기록이 연결된 구성원의 삭제는 원본 보호를 위해 제한합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
        if (section == SettingsSection.MEMBER_EDIT) item {
            val member = editMember
            if (member == null || member !in household.members) {
                Text("구성원 정보가 변경되었습니다. 목록에서 다시 선택해 주세요.", color = palette.error)
            } else {
                val validation = memberNameError(replacement, household.members, household.members.indexOf(member))
                Column(verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)) {
                    Text("현재 이름: $member", style = MaterialTheme.typography.bodyLarge)
                    Text("기존 배정 및 완료 기록의 이름도 함께 수정합니다.", color = palette.muted)
                    OutlinedTextField(value = replacement, onValueChange = { if (it.length <= 12) replacement = it },
                        label = { Text("새 이름") }, singleLine = true, enabled = !memberBusy,
                        modifier = Modifier.fillMaxWidth().testTag("homecrew-member-rename-input"))
                    validation?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
                    HomeCrewPrimaryButton("저장", onClick = { changeMember(member, replacement.trim()) },
                        enabled = !memberBusy && validation == null && replacement.trim() != member,
                        modifier = Modifier.fillMaxWidth().testTag("homecrew-member-rename-confirm"))
                }
            }
        }
        if (section == SettingsSection.THEME) item {
            Text("테마 선택", style = MaterialTheme.typography.titleMedium)
            Text("현재 ${themeMode.label}", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    HomeCrewFilterChip(
                        label = mode.label,
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) }
                    )
                }
            }
        }
        if (section == SettingsSection.REMINDER) item {
            Text("오늘 할 일 알림", style = MaterialTheme.typography.titleMedium)
            Text(
                "선택한 시간 이후 하루 한 번 남은 집안일 수를 알려드립니다. " +
                    "배터리 절약 정책에 따라 실제 전달 시각은 달라질 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("알림 사용", modifier = Modifier.padding(vertical = 12.dp))
                Switch(
                    checked = reminderSettings.enabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            reminderSettings = reminderPrefs.updateEnabled(false)
                            HomeCrewReminderScheduler.apply(context, reschedule = true)
                            reminderNotice = "알림을 해제했습니다."
                        } else if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            reminderSettings = reminderPrefs.updateEnabled(true)
                            HomeCrewReminderScheduler.apply(context, reschedule = true)
                            reminderNotice = "오늘 할 일 알림을 설정했습니다."
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "오늘 할 일 알림 사용" }
                )
            }
            OutlinedTextField(
                value = reminderTimeText,
                onValueChange = { reminderTimeText = it; reminderNotice = null },
                label = { Text("알림 시간 (24시간제 HH:mm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val valid = HomeCrewReminderTime.parse(reminderTimeText)
                    if (valid == null) {
                        reminderNotice = "알림 시간을 00:00부터 23:59까지 입력해 주세요."
                    } else {
                        reminderSettings = reminderPrefs.updateTime(valid)
                        reminderTimeText = valid.toString()
                        HomeCrewReminderScheduler.apply(context, reschedule = true)
                        reminderNotice = "알림 시간을 기기에 저장했습니다."
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("알림 시간 저장") }
            if (reminderNotice != null) {
                Text(reminderNotice.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        if (section == SettingsSection.ACCOUNT) {
            val signIn = homeCrewGoogleSignIn
            item {
                if (signIn != null) HomeCrewGoogleAccountSetting(signIn, onBusyChanged = { accountBusy = it })
                else {
                    Text("로그인 없이 사용 중", style = MaterialTheme.typography.titleMedium)
                    Text("현재 빌드에서는 이 기기의 자료를 관리할 수 있습니다.", color = palette.muted)
                }
            }
        }
        if (section == SettingsSection.PRIVACY) item {
            Text("데이터 보관", style = MaterialTheme.typography.titleMedium)
            Text(
                "집·구성원·집안일·완료 기록은 이 기기에 저장됩니다. 다른 기기와 자동 동기화되지 않으며 앱 데이터 삭제 시 기록이 사라질 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
            TextButton(onClick = {
                privacyLinkError = !openHomeCrewWebPage(context, HOMECREW_PRIVACY_POLICY_URL)
            }) { Text("개인정보처리방침") }
            if (privacyLinkError) {
                Text(
                    "브라우저에서 개인정보처리방침을 열 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.error
                )
            }
        }
        if (error != null) {
            item { Text(error.orEmpty(), color = palette.error) }
        }
        if (notice != null) {
            item { Text(notice.orEmpty(), color = palette.muted) }
        }
    }
    }
    }
    deleteMember?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteMember = null },
            title = { Text("구성원을 삭제할까요?") },
            text = { Text("미사용 구성원 $member 의 설정만 삭제합니다. 기존 집안일이나 기록은 삭제하지 않습니다.") },
            confirmButton = { TextButton(enabled = !memberBusy, onClick = {
                deleteMember = null
                changeMember(member, null)
            }, modifier = Modifier.testTag("homecrew-member-delete-confirm")) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleteMember = null }) { Text("취소") } }
        )
    }
}
