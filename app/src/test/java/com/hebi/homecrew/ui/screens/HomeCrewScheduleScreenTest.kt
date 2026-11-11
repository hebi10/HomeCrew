package com.hebi.homecrew.ui.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewScheduleScreenTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun monthNavigationHandlesLeapDayAndYearBoundary() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            movedScheduleDate(LocalDate.of(2028, 3, 31), CalendarMode.MONTH, -1)
        )
        assertEquals(
            LocalDate.of(2027, 1, 6),
            movedScheduleDate(LocalDate.of(2026, 12, 30), CalendarMode.WEEK, 1)
        )
        assertEquals(
            LocalDate.of(2027, 1, 1),
            movedScheduleDate(LocalDate.of(2026, 12, 31), CalendarMode.DAY, 1)
        )
    }

    @Test
    fun memberFilterChangesCalendarCountsAndCompletionTogether() {
        val chores = listOf(
            DemoChore("a", ChoreDefinition("a", "청소", "청소", "나", "매일", 2, "10:00"), today),
            DemoChore("b", ChoreDefinition("b", "설거지", "주방", "지민", "매일", 1, "11:00"), today, completed = true),
            DemoChore("c", ChoreDefinition("c", "분리수거", "쓰레기", "나", "매일", 1, "12:00"), today, completed = true),
            DemoChore("d", ChoreDefinition("d", "빨래", "세탁", "지민", "매일", 1, "13:00"), today.plusDays(1))
        )
        assertEquals(
            ScheduleDaySummary(3, 2),
            scheduleDaySummaries(scheduleChoresForMember(chores, ALL_SCHEDULE_MEMBERS))[today]
        )
        assertEquals(
            ScheduleDaySummary(2, 1),
            scheduleDaySummaries(scheduleChoresForMember(chores, "나"))[today]
        )
        assertEquals(
            ScheduleDaySummary(1, 1),
            scheduleDaySummaries(scheduleChoresForMember(chores, "지민"))[today]
        )
        assertEquals(2, scheduleChoresForMember(chores, "지민").size)
        assertEquals("1/2", ScheduleDaySummary(2, 1).statusLabel)
        assertEquals("완료", ScheduleDaySummary(1, 1).statusLabel)
        assertEquals("2건", ScheduleDaySummary(2, 0).statusLabel)
        assertEquals("–", ScheduleDaySummary(0, 0).statusLabel)
    }

    @Test
    fun newChoreDateIsLimitedToPersistedOccurrenceWindow() {
        assertFalse(canCreateScheduleChore(today.minusDays(1), today))
        assertTrue(canCreateScheduleChore(today, today))
        assertTrue(canCreateScheduleChore(today.plusDays(62), today))
        assertFalse(canCreateScheduleChore(today.plusDays(63), today))
    }

    @Test
    fun selectedScheduleDatePrefillsNewDraftWithoutChangingEditorBehavior() {
        val members = listOf("나", "지민")
        val model = DemoChoreViewModel()
        val selectedDate = model.today.plusDays(3)
        model.openEditor(null, members, selectedDate)
        assertEquals(selectedDate, model.editor.value?.startDate)
        assertEquals(listOf("나"), model.editor.value?.assignment?.members)

        model.openEditor(null, members)
        assertEquals(model.today, model.editor.value?.startDate)

        val definition = ChoreDefinition(
            id = "existing",
            name = "기존 집안일",
            category = "주방",
            owner = "지민",
            repeat = "매일",
            points = 2,
            time = "20:00",
            startDate = model.today.minusDays(2)
        )
        model.openEditor(definition, members, selectedDate)
        assertEquals(definition.startDate, model.editor.value?.startDate)
    }
}
