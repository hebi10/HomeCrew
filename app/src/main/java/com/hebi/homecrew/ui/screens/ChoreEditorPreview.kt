package com.hebi.homecrew.ui.screens

import java.time.LocalDate

internal data class EditorOccurrencePreview(
    val date: LocalDate,
    val time: String,
    val owner: String
)

internal const val EDITOR_PREVIEW_DAYS_AHEAD = 62L

internal fun upcomingEditorOccurrences(
    draft: ChoreEditorDraft,
    today: LocalDate,
    baseline: Map<String, Int> = emptyMap(),
    assignment: ChoreAssignment = draft.assignment
): List<EditorOccurrencePreview> {
    if (assignment.members.isEmpty()) return emptyList()
    return previewDraftInstances(
        draft = draft.copy(assignment = assignment),
        today = today,
        baseline = baseline,
        daysAhead = EDITOR_PREVIEW_DAYS_AHEAD
    ).take(3).map {
        EditorOccurrencePreview(
            date = it.date,
            time = it.definition.time,
            owner = it.definition.owner
        )
    }
}

internal fun repeatPreviewDates(
    startDate: LocalDate,
    today: LocalDate,
    rule: ChoreRepeat
): List<LocalDate> =
    nextChoreDates(
        startDate = startDate,
        rule = rule,
        from = today,
        to = today.plusDays(EDITOR_PREVIEW_DAYS_AHEAD)
    ).take(3)

internal fun editorPreviewHeading(rule: ChoreRepeat): String =
    if (rule.mode == ChoreRepeatMode.NONE) "예정 일정"
    else "다가오는 일정 · 최대 3회"
