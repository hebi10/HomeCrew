package com.hebi.homecrew.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewTaskRow
import com.hebi.homecrew.ui.model.PreviewChore
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.time.LocalDate

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = LocalHomeCrewColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "화면 제목: $title" }
        )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = palette.muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionHeading(
    title: String,
    trailing: String? = null
) {
    val palette = LocalHomeCrewColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (trailing != null) {
            Text(
                trailing,
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun BorderedSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .background(palette.surface, RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
fun DemoChoreRow(
    task: DemoChore,
    today: LocalDate,
    onToggle: () -> Unit,
    onDetails: () -> Unit
) {
    HomeCrewTaskRow(
        chore = PreviewChore(
            id = task.id.hashCode(),
            title = task.definition.name,
            detail = "${task.definition.time} · ${task.definition.owner} · ${task.definition.category}",
            status = demoChoreStatus(task, today)
        ),
        onToggleComplete = onToggle,
        onOpenDetails = onDetails
    )
}

@Composable
fun DemoChoreDetails(
    task: DemoChore?,
    onDismiss: () -> Unit
) {
    if (task == null) return
    val palette = LocalHomeCrewColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.definition.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("담당: ${task.definition.owner}")
                Text("카테고리: ${task.definition.category}")
                Text("예정일: ${task.date} · ${task.definition.time}")
                Text("반복: ${task.definition.repeat} · 난이도 ${task.definition.points}점")
                Text(
                    "저장한 집안일과 완료 기록은 현재 기기에서 유지됩니다.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
fun EmptyResult(title: String, description: String) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
    }
}
