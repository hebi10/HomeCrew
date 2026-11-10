package com.hebi.homecrew.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.components.HomeCrewTextField
import com.hebi.homecrew.ui.screens.BorderedSection
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors

@Composable
fun HomeCrewSetupFlow(
    onFinished: (HouseholdSetup) -> Boolean,
    initialStep: Int = 0
) {
    val palette = LocalHomeCrewColors.current
    var step by rememberSaveable { mutableStateOf(initialStep) }
    var homeName by rememberSaveable { mutableStateOf("우리 집") }
    var members by rememberSaveable { mutableStateOf(listOf("나")) }
    var defaultMember by rememberSaveable { mutableStateOf("나") }
    var memberInput by rememberSaveable { mutableStateOf("") }
    var editingIndex by rememberSaveable { mutableStateOf(-1) }
    var selectedIds by rememberSaveable { mutableStateOf(suggestedTemplateIds) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val goBack: () -> Unit = {
        if (step > 0) {
            step--
            error = null
        }
    }
    BackHandler(enabled = step > 0, onBack = goBack)

    fun finishSetup(templateIds: List<String>) {
        val result = HouseholdSetup(
            homeName = homeName.trim(),
            members = members,
            defaultMember = defaultMember,
            selectedTemplateIds = templateIds
        )
        error = if (onFinished(result)) null else "설정을 저장하지 못했습니다. 다시 시도해 주세요."
    }

    fun next() {
        error = null
        when (step) {
            0 -> step = 1
            1 -> {
                error = houseNameError(homeName)
                if (error == null) step = 2
            }
            2 -> {
                error = when {
                    memberInput.isNotBlank() || editingIndex >= 0 -> "입력한 이름을 먼저 추가하거나 수정해 주세요."
                    members.isEmpty() -> "구성원을 한 명 이상 등록해 주세요."
                    defaultMember !in members -> "기본 구성원을 선택해 주세요."
                    else -> null
                }
                if (error == null) step = 3
            }
            3 -> finishSetup(selectedIds)
        }
    }

    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        horizontal = HomeCrewDimensions.screenPadding,
                        vertical = HomeCrewDimensions.space12
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (step == 3 && selectedIds.isNotEmpty()) {
                    TextButton(
                        onClick = { finishSetup(emptyList()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("템플릿 없이 건너뛰기")
                    }
                }
                val primaryLabel = when (step) {
                    0 -> "시작하기"
                    1, 2 -> "다음"
                    else -> if (selectedIds.isEmpty()) "건너뛰고 홈 보기"
                    else "선택한 집안일로 시작"
                }
                key(step, primaryLabel) {
                    HomeCrewPrimaryButton(
                        text = primaryLabel,
                        onClick = ::next,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != null) {
                    Text(
                        text = requireNotNull(error),
                        color = palette.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(HomeCrewDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space24)
        ) {
            if (step > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = goBack) { Text("이전") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$step / 3",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.muted
                    )
                }
                LinearProgressIndicator(
                    progress = { step.toFloat() / 3f },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.ink,
                    trackColor = palette.border
                )
            }
            when (step) {
                0 -> {
                    SetupTitle(
                        title = "집안일을 함께, 더 간단하게",
                        subtitle = "한 기기에서 우리 집의 할 일과 분담 현황을 확인합니다."
                    )
                    listOf(
                        "오늘의 할 일" to "남은 집안일을 한눈에 확인합니다.",
                        "우리 집 구성원" to "가족이나 동거인에게 집안일을 배정할 수 있습니다.",
                        "완료 현황" to "주간·월간 진행 상황을 숫자로 확인합니다."
                    ).forEach { (title, desc) ->
                        BorderedSection {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Text(desc, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                            }
                        }
                    }
                    Text(
                        "로그인이나 초대 없이 시작합니다. 집 설정과 집안일·완료 기록은 이 기기에 저장되며, 다른 기기와 공유되지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
                1 -> {
                    SetupTitle(
                        title = "우리 집 만들기",
                        subtitle = "집 이름을 정해 주세요. 설정은 현재 기기에만 저장됩니다."
                    )
                    HomeCrewTextField(
                        label = "집 이름",
                        value = homeName,
                        onValueChange = {
                            homeName = it
                            error = null
                        },
                        placeholder = "우리 집",
                        isError = houseNameError(homeName) != null
                    )
                    Text(
                        "${homeName.trim().length} / 20자",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                2 -> {
                    SetupTitle(
                        title = "함께할 사람 등록",
                        subtitle = "한 명부터 시작할 수 있습니다. 다른 기기를 초대하는 기능은 아직 없습니다."
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeCrewTextField(
                            label = if (editingIndex >= 0) "구성원 이름 수정" else "새 구성원 이름",
                            value = memberInput,
                            onValueChange = {
                                memberInput = it
                                error = null
                            },
                            placeholder = "이름 또는 별명"
                        )
                        HomeCrewSecondaryButton(
                            text = if (editingIndex >= 0) "이름 수정 완료" else "+ 구성원 추가",
                            onClick = {
                                error = memberNameError(
                                    memberInput,
                                    members,
                                    editingIndex.takeIf { it >= 0 }
                                )
                                if (error == null) {
                                    val name = memberInput.trim()
                                    if (editingIndex >= 0) {
                                        val old = members[editingIndex]
                                        members = members.toMutableList().also { it[editingIndex] = name }
                                        if (defaultMember == old) defaultMember = name
                                    } else {
                                        members = members + name
                                    }
                                    memberInput = ""
                                    editingIndex = -1
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text("등록된 구성원 · ${members.size}명", style = MaterialTheme.typography.titleMedium)
                    members.forEachIndexed { index, member ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .background(palette.surface)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = defaultMember == member,
                                    onClick = { defaultMember = member }
                                )
                                Text(
                                    member,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (defaultMember == member) {
                                    Text(
                                        "기본 구성원",
                                        color = palette.muted,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    memberInput = member
                                    editingIndex = index
                                    error = null
                                }) { Text("수정") }
                                TextButton(
                                    enabled = members.size > 1,
                                    onClick = {
                                        members = members.filterIndexed { i, _ -> i != index }
                                        if (defaultMember == member) defaultMember = members.first()
                                        if (editingIndex == index) {
                                            memberInput = ""
                                            editingIndex = -1
                                        } else if (editingIndex > index) editingIndex--
                                    }
                                ) { Text("삭제") }
                            }
                        }
                    }
                    Text(
                        "기본 구성원은 홈에서 처음 표시할 사람입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
                else -> {
                    SetupTitle(
                        title = "집안일을 선택하세요",
                        subtitle = "필요한 항목만 고를 수 있고, 선택하지 않고 시작해도 됩니다."
                    )
                    Text(
                        "${selectedIds.size}개 선택 · 담당자는 임시로 순서대로 배정됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                    homeCrewTemplates.groupBy { it.category }.forEach { (category, templates) ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(category, style = MaterialTheme.typography.titleMedium)
                            templates.forEach { template ->
                                val checked = template.id in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                        .background(palette.surface)
                                        .clickable {
                                            selectedIds = if (checked) selectedIds - template.id
                                            else selectedIds + template.id
                                        }
                                        .heightIn(min = 64.dp)
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            template.repeat + " · 예정 " + template.time,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.muted
                                        )
                                    }
                                    Text(
                                        "${template.points}점",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = palette.muted
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "선택한 집안일과 반복 일정·완료 기록은 이 기기에 저장됩니다. 담당 방식과 반복 설정은 나중에 수정할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupTitle(title: String, subtitle: String) {
    val palette = LocalHomeCrewColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewSetupIntro() {
    HomeCrewTheme(darkTheme = false) {
        HomeCrewSetupFlow(onFinished = { true })
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PreviewSetupMembersDark() {
    HomeCrewTheme(darkTheme = true) {
        HomeCrewSetupFlow(onFinished = { true }, initialStep = 2)
    }
}
