package com.hebi.homecrew.ui.screens

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreDetailTimelineTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val definition = ChoreDefinition(
        id = "dishes",
        name = "설거지",
        category = "주방",
        owner = "지민",
        repeat = "매일",
        points = 2,
        time = "20:00"
    )

    private fun chore(
        id: String,
        offset: Long,
        done: Boolean = false,
        completedBy: String? = null,
        completedAt: Long? = null
    ) = DemoChore(
        id = id,
        definition = definition,
        date = today.plusDays(offset),
        completed = done,
        completedBy = completedBy,
        completedAtEpochMillis = completedAt
    )

    @Test
    fun selectedIdHasPriorityOverTodayAndUpcoming() {
        val old = chore("past", -3)
        val onDate = chore("today", 0)
        val future = chore("future", 2)
        val records = listOf(future, old, onDate)

        assertEquals("past", selectedChoreOccurrence(records, "past", today)?.id)
        assertEquals("today", selectedChoreOccurrence(records, null, today)?.id)
        assertEquals("today", selectedChoreOccurrence(records, "stale-id", today)?.id)
        assertEquals("future", selectedChoreOccurrence(listOf(old, future), null, today)?.id)
        assertEquals("past", selectedChoreOccurrence(listOf(old), null, today)?.id)
        assertNull(selectedChoreOccurrence(emptyList(), null, today))
    }

    @Test
    fun upcomingContainsOnlyPendingFutureAndTodayInChronologicalOrder() {
        val incoming = listOf(
            chore("future3", 3),
            chore("done-today", 0, done = true),
            chore("past", -1),
            chore("future1", 1),
            chore("today", 0),
            chore("future2", 2)
        )
        assertEquals(
            listOf("today", "future1", "future2"),
            upcomingChoreOccurrences(incoming, today).map { it.id }
        )
        assertTrue(upcomingChoreOccurrences(listOf(chore("done", 0, true)), today).isEmpty())
    }

    @Test
    fun missedOccurrencesExcludeCompletedAndSelectNewestThree() {
        val records = listOf(
            chore("old", -10),
            chore("miss3", -3),
            chore("miss2", -2),
            chore("miss1", -1),
            chore("done-past", -1, true),
            chore("future", 1)
        )
        assertEquals(
            listOf("miss1", "miss2", "miss3"),
            missedChoreOccurrences(records, today).map { it.id }
        )
        assertTrue(missedChoreOccurrences(listOf(chore("done-past", -1, true)), today).isEmpty())
    }

    @Test
    fun recentCompletedPrefersActualCompletionTimestampOverScheduledDate() {
        val records = listOf(
            chore("newly-done-old-task", -5, true, "민수", 3_000L),
            chore("previous", -2, true, "지민", 2_000L),
            chore("latest-date-but-earlier-done", 0, true, "나", 1_000L),
            chore("unknown-metadata", 0, true),
            chore("pending", 1)
        )
        val completed = recentCompletedChoreOccurrences(records)
        assertEquals(
            listOf("newly-done-old-task", "previous", "latest-date-but-earlier-done"),
            completed.map { it.id }
        )
        assertFalse(completed.any { !it.completed })
    }

    @Test
    fun recordsWithoutMetadataStayHonestAndTimeFormattingUsesProvidedZone() {
        assertNull(recordedCompletionTime(null, ZoneOffset.UTC))
        assertEquals("1970.01.01 00:00", recordedCompletionTime(0L, ZoneOffset.UTC))
        val oldRecord = chore("historic", -10, done = true)
        assertNull(oldRecord.completedBy)
        assertNull(oldRecord.completedAtEpochMillis)
        assertEquals(listOf("historic"), recentCompletedChoreOccurrences(listOf(oldRecord)).map { it.id })
    }

    @Test
    fun selectedCompletedOccurrenceRemainsSelectedAfterToggleOrUndo() {
        val target = chore("target", -1)
        val future = chore("later", 1)
        val completed = target.copy(
            completed = true,
            completedBy = "민수",
            completedAtEpochMillis = 1_800_000_000_000L
        )
        assertEquals(
            completed,
            selectedChoreOccurrence(listOf(future, completed), "target", today)
        )
        val restored = completed.copy(
            completed = false,
            completedBy = null,
            completedAtEpochMillis = null
        )
        assertEquals(
            restored,
            selectedChoreOccurrence(listOf(future, restored), "target", today)
        )
        assertTrue(recentCompletedChoreOccurrences(listOf(future, restored)).isEmpty())
    }
}
