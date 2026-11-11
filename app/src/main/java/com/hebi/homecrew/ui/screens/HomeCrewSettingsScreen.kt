package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.fillMaxWidth()
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
            Text("데이터 보관", style = MaterialTheme.typography.titleMedium)
            Text(
                "집·구성원·집안일·완료 기록은 이 기기에 저장됩니다. 다른 기기와 자동 동기화되지 않으며 앱 데이터 삭제 시 기록이 사라질 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }
        if (error != null) {
            item { Text(error.orEmpty(), color = palette.error) }
        }
        if (notice != null) {
            item { Text(notice.orEmpty(), color = palette.muted) }
        }
    }
}
