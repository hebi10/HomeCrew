package com.hebi.homecrew.ui.screens

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreEditorPreviewTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val participants = listOf("나", "지민", "민수")

    private fun draft(
        repeat: ChoreRepeat = ChoreRepeat(ChoreRepeatMode.DAILY),
        assignment: ChoreAssignment = ChoreAssignment(members = listOf("나")),
        startDate: LocalDate = today
    ) = ChoreEditorDraft(
        name = "설거지",
        startDate = startDate,
        time = "19:30",
        points = 2,
        repeat = repeat,
        assignment = assignment
    )

    @Test
    fun oneTimePreviewHasOneDateAndAccurateHeading() {
        val start = today.plusDays(4)
        val single = draft(repeat = ChoreRepeat(), startDate = start)
        assertEquals("예정 일정", editorPreviewHeading(single.repeat))
        assertEquals(
            listOf(start),
            repeatPreviewDates(start, today, single.repeat)
        )
        assertEquals(
            listOf(EditorOccurrencePreview(start, "19:30", "나")),
            upcomingEditorOccurrences(single, today)
        )
    }

    @Test
    fun weeklyAndBiweeklyPreviewUseActualNextScheduledDates() {
        val wed = ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(DayOfWeek.WEDNESDAY))
        val biweekly = wed.copy(mode = ChoreRepeatMode.BIWEEKLY)
        assertEquals(
            listOf(today, today.plusDays(7), today.plusDays(14)),
            repeatPreviewDates(today, today, wed)
        )
        assertEquals(
            listOf(today, today.plusDays(14), today.plusDays(28)),
            repeatPreviewDates(today, today, biweekly)
        )
        assertEquals("다가오는 일정 · 최대 3회", editorPreviewHeading(wed))
    }

    @Test
    fun roundRobinPreviewMatchesActualGeneratedOccurrencesAndMemberOrder() {
        val roundRobin = ChoreAssignment(
            ChoreAssignmentMode.ROUND_ROBIN, participants
        )
        val value = draft(assignment = roundRobin)
        val actual = upcomingEditorOccurrences(value, today)
        assertEquals(participants, actual.map { it.owner })
        assertEquals(
            listOf(today, today.plusDays(1), today.plusDays(2)),
            actual.map { it.date }
        )
        assertEquals(
            previewDraftInstances(value, today).take(3).map {
                EditorOccurrencePreview(it.date, it.definition.time, it.definition.owner)
            },
            actual
        )
    }

    @Test
    fun workloadBalanceUsesSameBaselineAndDeterministicTieOrderAsPersistence() {
        val balanced = ChoreAssignment(
            ChoreAssignmentMode.BALANCED, listOf("나", "지민")
        )
        val baseline = mapOf("나" to 4, "지민" to 0)
        val actual = upcomingEditorOccurrences(draft(assignment = balanced), today, baseline)
        assertEquals(listOf("지민", "지민", "나"), actual.map { it.owner })
        assertEquals(
            listOf(today, today.plusDays(1), today.plusDays(2)),
            actual.map { it.date }
        )
    }

    @Test
    fun emptyParticipantsAndOutsideTheSixtyTwoDayHorizonAreExplicitlyEmpty() {
        assertTrue(upcomingEditorOccurrences(
            draft(assignment = ChoreAssignment(
                ChoreAssignmentMode.ROUND_ROBIN, emptyList()
            )), today
        ).isEmpty())
        val future = today.plusDays(63)
        assertTrue(upcomingEditorOccurrences(
            draft(startDate = future), today
        ).isEmpty())
        assertTrue(repeatPreviewDates(future, today, ChoreRepeat()).isEmpty())
    }

    @Test
    fun monthEndAndLeapDayDatesAgreeWithSharedRecurrenceCalculation() {
        val jan31 = LocalDate.of(2028, 1, 31)
        val expected = listOf(jan31, LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31))
        val recurring = ChoreRepeat(ChoreRepeatMode.MONTHLY)
        assertEquals(expected, repeatPreviewDates(jan31, jan31, recurring))
        assertEquals(expected, upcomingEditorOccurrences(
            draft(repeat = recurring, startDate = jan31), jan31
        ).map { it.date })
    }
}
