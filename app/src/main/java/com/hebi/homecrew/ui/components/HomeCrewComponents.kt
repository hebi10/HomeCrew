package com.hebi.homecrew.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.model.PreviewChore
import com.hebi.homecrew.ui.model.completionPercent
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors

@Composable
fun HomeCrewPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    completionAction: Boolean = false
) {
    val palette = LocalHomeCrewColors.current
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = HomeCrewDimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (completionAction) palette.success else palette.ink,
            contentColor = palette.surface
        )
    ) {
        Text(text)
    }
}

@Composable
fun HomeCrewSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHomeCrewColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = HomeCrewDimensions.buttonHeight),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Text(text, color = palette.ink)
    }
}

@Composable
fun HomeCrewStatusChip(status: ChoreStatus) {
    val palette = LocalHomeCrewColors.current
    val (foreground, backgroundColor) = when (status) {
        ChoreStatus.PLANNED -> palette.muted to palette.subtle
        ChoreStatus.COMPLETED -> palette.success to palette.success.copy(alpha = 0.13f)
        ChoreStatus.SKIPPED -> palette.muted to palette.subtle
        ChoreStatus.OVERDUE -> palette.warning to palette.warning.copy(alpha = 0.13f)
    }
    val statusIcon = when (status) {
        ChoreStatus.PLANNED -> Icons.Outlined.Schedule
        ChoreStatus.COMPLETED -> Icons.Outlined.CheckCircle
        ChoreStatus.SKIPPED -> Icons.Outlined.Schedule
        ChoreStatus.OVERDUE -> Icons.Outlined.WarningAmber
    }
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = status.label,
                color = foreground,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun HomeCrewFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(4.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.surface,
            labelColor = palette.ink,
            selectedContainerColor = palette.ink,
            selectedLabelColor = palette.surface
        )
    )
}

@Composable
fun HomeCrewTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.fillMaxWidth()
    )
}

fun homeProgressLabel(completed: Int, total: Int): String = when {
    total <= 0 -> "오늘 예정된 집안일 없음"
    completed >= total -> "모두 완료"
    else -> "${completionPercent(completed, total)}% 완료"
}

@Composable
fun HomeCrewProgressCard(completed: Int, total: Int, showHouse: Boolean = false) {
    val palette = LocalHomeCrewColors.current
    val percent = completionPercent(completed, total)
    val barHeight = if (showHouse) 8.dp else 6.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.subtle, RoundedCornerShape(if (showHouse) 12.dp else 4.dp))
            .padding(horizontal = 16.dp, vertical = if (showHouse) 24.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space12)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘의 진행률", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)) {
                    Text(
                        text = "${completed.coerceIn(0, total.coerceAtLeast(0))} / ${total.coerceAtLeast(0)}",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (total > 0) "$percent% 완료" else "–",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.muted,
                        modifier = Modifier.align(Alignment.CenterVertically)
                            .semantics { contentDescription = homeProgressLabel(completed, total) },
                    )
                }
            }
            if (showHouse) {
                Box(
                    modifier = Modifier.size(62.dp)
                        .background(palette.warning.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Cottage, null, tint = palette.warning, modifier = Modifier.size(42.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(palette.border)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), 0f..100f)
                }
        ) {
            if (percent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent / 100f)
                        .height(barHeight)
                        .background(palette.success)
                )
            }
        }
    }
}

@Composable
fun HomeCrewTaskRow(
    chore: PreviewChore,
    onToggleComplete: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
) {
    val palette = LocalHomeCrewColors.current
    val completed = chore.status == ChoreStatus.COMPLETED
    val rowShape = RoundedCornerShape(if (category == null) 4.dp else 12.dp)
    val cardBorder = if (category == null) Modifier else Modifier.border(1.dp, palette.border, rowShape)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HomeCrewDimensions.taskRowMinHeight)
            .clip(rowShape)
            .background(palette.surface)
            .then(cardBorder)
            .clickable(onClick = onOpenDetails)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(HomeCrewDimensions.touchTarget)
        ) {
            Icon(
                imageVector = if (completed) Icons.Outlined.CheckBox
                    else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (completed) "완료 취소" else "완료 처리",
                tint = if (completed) palette.success else palette.muted
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = chore.title,
                color = if (completed) palette.muted else palette.ink,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = chore.detail,
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (category != null) {
            ChoreCategoryIcon(category)
            Spacer(Modifier.size(8.dp))
        }
        HomeCrewStatusChip(chore.status)
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.padding(start = 8.dp).size(18.dp)
        )
    }
}

@Composable
private fun ChoreCategoryIcon(category: String) {
    val palette = LocalHomeCrewColors.current
    val (icon, tint) = when (category) {
        "주방" -> Icons.Outlined.Restaurant to palette.warning
        "청소" -> Icons.Outlined.CleaningServices to MaterialTheme.colorScheme.secondary
        "쓰레기" -> Icons.Outlined.Recycling to palette.success
        "세탁" -> Icons.Outlined.LocalLaundryService to MaterialTheme.colorScheme.secondary
        "반려동물" -> Icons.Outlined.Pets to palette.warning
        else -> Icons.Outlined.Home to palette.muted
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

enum class HomeTab(val label: String) {
    HOME("홈"),
    SCHEDULE("일정"),
    CHORES("집안일"),
    STATS("통계")
}

@Composable
fun HomeCrewBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    NavigationBar(containerColor = palette.surface, tonalElevation = 0.dp) {
        HomeTab.entries.forEach { tab ->
            val imageVector = when (tab) {
                HomeTab.HOME -> Icons.Outlined.Home
                HomeTab.SCHEDULE -> Icons.Outlined.CalendarMonth
                HomeTab.CHORES -> Icons.Outlined.Checklist
                HomeTab.STATS -> Icons.Outlined.BarChart
            }
            NavigationBarItem(
                modifier = Modifier.semantics { contentDescription = "${tab.label} 탭" },
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = imageVector, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.ink,
                    selectedTextColor = palette.ink,
                    unselectedIconColor = palette.muted,
                    unselectedTextColor = palette.muted,
                    indicatorColor = palette.surface
                )
            )
        }
    }
}

@Composable
fun HomeCrewEmptyState(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = palette.muted
        )
        Text(
            title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            description,
            textAlign = TextAlign.Center,
            color = palette.muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        HomeCrewPrimaryButton(text = actionLabel, onClick = onAction)
    }
}
