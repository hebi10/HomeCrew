package com.hebi.homecrew.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
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
    enabled: Boolean = true
) {
    val palette = LocalHomeCrewColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(HomeCrewDimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.ink,
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
        modifier = modifier.height(HomeCrewDimensions.buttonHeight),
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
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(3.dp)
    ) {
        Text(
            text = status.label,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
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

@Composable
fun HomeCrewProgressCard(completed: Int, total: Int) {
    val palette = LocalHomeCrewColors.current
    val percent = completionPercent(completed, total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.subtle, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("오늘의 진행률", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${completed.coerceIn(0, total.coerceAtLeast(0))} / ${total.coerceAtLeast(0)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "  ${percent}% 완료",
                modifier = Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = palette.ink,
            trackColor = palette.border
        )
    }
}

@Composable
fun HomeCrewTaskRow(
    chore: PreviewChore,
    onToggleComplete: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHomeCrewColors.current
    val completed = chore.status == ChoreStatus.COMPLETED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HomeCrewDimensions.taskRowMinHeight)
            .clickable(onClick = onOpenDetails)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(HomeCrewDimensions.touchTarget)
        ) {
            Icon(
                imageVector = if (completed) Icons.Filled.CheckCircle
                    else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (completed) "완료 취소" else "완료 처리",
                tint = if (completed) palette.success else palette.muted
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(start = 4.dp),
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
        HomeCrewStatusChip(chore.status)
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.padding(start = 8.dp).size(18.dp)
        )
    }
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
    NavigationBar(containerColor = palette.surface) {
        HomeTab.entries.forEach { tab ->
            val imageVector = when (tab) {
                HomeTab.HOME -> Icons.Outlined.Home
                HomeTab.SCHEDULE -> Icons.Outlined.CalendarMonth
                HomeTab.CHORES -> Icons.Outlined.FormatListBulleted
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
                    indicatorColor = palette.subtle
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
