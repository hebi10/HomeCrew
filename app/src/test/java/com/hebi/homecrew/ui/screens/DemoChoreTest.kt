package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.model.ChoreStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoChoreTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `home demo contains seven chores and four completed`() {
        val chores = tasksForDay(sampleChores(today), today)
        val summary = summaryFor(chores)
        assertEquals(7, summary.total)
        assertEquals(4, summary.completed)
        assertEquals(57, summary.percent)
    }

    @Test
    fun `date grouping obeys Monday week start and month boundaries`() {
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(today))
        val chores = sampleChores(today)
        assertTrue(tasksForWeek(chores, today).all {
            !it.date.isBefore(LocalDate.of(2026, 11, 9)) && it.date.isBefore(LocalDate.of(2026, 11, 16))
        })
        assertTrue(tasksForMonth(chores, today).all { it.date.monthValue == 11 })
    }

    @Test
    fun `past missed chores are overdue but future chores are planned`() {
        val all = sampleChores(today)
        val past = all.first { it.date.isBefore(today) && !it.completed }
        val future = all.first { it.date.isAfter(today) }
        assertEquals(ChoreStatus.OVERDUE, demoChoreStatus(past, today))
        assertEquals(ChoreStatus.PLANNED, demoChoreStatus(future, today))
    }

    @Test
    fun `search category and status filter compose together`() {
        val all = sampleChores(today)
        val entries = filteredDefinitions(choreDefinitions(), all, "설거지", "주방", "예정", today)
        assertEquals(listOf("dishes"), entries.map { it.id })
        assertTrue(filteredDefinitions(choreDefinitions(), all, "없는 집안일", "전체", "전체", today).isEmpty())
        assertFalse(filteredDefinitions(choreDefinitions(), all, "", "전체", "완료", today).isEmpty())
    }

    @Test
    fun `summary points calculate by actual completion`() {
        val chores = tasksForDay(sampleChores(today), today)
        val s = summaryFor(chores)
        assertEquals(17, s.assignedPoints)
        assertEquals(8, s.completedPoints)
    }
}
