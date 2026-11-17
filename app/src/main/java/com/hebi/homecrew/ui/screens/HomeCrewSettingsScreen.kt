package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun HomeCrewSettingsScreen(
    household: HouseholdSetup,
    assignedMembers: Set<String>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSave: (HouseholdSetup) -> Boolean,
    onBack: () -> Unit
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
    var homeName by remember(household.homeName) { mutableStateOf(household.homeName) }
    var newMember by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun persist(updated: HouseholdSetup): Boolean {
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
        LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space16
        ),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space16)
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("뒤로")
            }
            Text("설정", style = MaterialTheme.typography.headlineSmall)
            Text("이 기기의 집안일과 구성원을 관리합니다.", color = palette.muted)
        }
        item {
            Text("공동 공간", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = homeName,
                onValueChange = { homeName = it; error = null; notice = null },
                label = { Text("집 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val validation = houseNameError(homeName)
                    if (validation != null) error = validation
                    else persist(household.copy(homeName = homeName.trim()))
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("집 이름 저장") }
        }
        item {
            Text("구성원 관리", style = MaterialTheme.typography.titleMedium)
            Text("기본 구성원을 선택하거나 새 구성원을 추가할 수 있습니다.", color = palette.muted)
            household.members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(member, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(
                        onClick = { persist(household.copy(defaultMember = member)) },
                        enabled = household.defaultMember != member,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (household.defaultMember == member) "기본 구성원" else "기본으로 설정")
                    }
                }
            }
            OutlinedTextField(
                value = newMember,
                onValueChange = { newMember = it; error = null; notice = null },
                label = { Text("새 구성원 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "새 구성원 이름 입력" }
            )
            Button(
                onClick = {
                    val validation = memberNameError(newMember, household.members)
                    if (validation != null) error = validation
                    else if (persist(household.copy(members = household.members + newMember.trim()))) {
                        newMember = ""
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("구성원 추가") }
            Text(
                "기존 집안일·완료 기록에 연결된 구성원의 이름 변경과 삭제는 데이터 이전 기능이 준비된 후 지원합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
        item {
            Text("화면 테마", style = MaterialTheme.typography.titleMedium)
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
        item {
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
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("알림 시간 저장") }
            if (reminderNotice != null) {
                Text(reminderNotice.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        homeCrewGoogleSignIn?.let { signIn ->
            item { HomeCrewGoogleAccountSetting(signIn) }
        }
        item {
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
