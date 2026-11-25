package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.cloud.RemoteChore
import com.hebi.homecrew.data.cloud.RemoteHouseholdMember
import com.hebi.homecrew.data.cloud.RemoteOccurrence
import com.hebi.homecrew.data.cloud.RemoteSchedule
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewSharedScheduleRefillTest {
    private val day = LocalDate.of(2026, 11, 25)
    private val epoch = day.toEpochDay()
    private val rule = RemoteSchedule(epoch, "DAILY", assignmentMode = "FIXED", memberUids = listOf("owner"))
    private val chore = RemoteChore("task", "설거지", "주방", 2, "owner", 4, rule)
    private fun snapshot(rows: List<RemoteOccurrence> = emptyList()) = RemoteSnapshot(
        listOf(chore), rows, false, listOf(RemoteHouseholdMember("owner", true))
    )
    private fun row(offset: Long) = RemoteOccurrence(
        "row-$offset", chore.id, epoch + offset, "owner", false, null, 1
    )

    @Test fun fillsOnlyMissingDatesInCurrentHorizon() {
        val request = missingSharedScheduleRequests(snapshot(listOf(row(0), row(1))), "owner", day).single()
        assertEquals(epoch + 2, request.firstMissingDay)
        assertEquals(epoch, request.fromEpochDay)
        assertEquals(epoch + 62, request.toEpochDay)
        assertEquals(4L, request.revision)
        val full = snapshot((0L..62L).map(::row))
        assertTrue(missingSharedScheduleRequests(full, "owner", day).isEmpty())
        assertEquals(epoch + 63, missingSharedScheduleRequests(full, "owner", day.plusDays(1)).single().firstMissingDay)
    }

    @Test fun cancelledAndCompletedRowsAreNeverRegenerated() {
        val full = (0L..62L).map(::row).toMutableList()
        full[0] = full[0].copy(scheduleCancelled = true)
        full[1] = full[1].copy(skipped = true)
        full[2] = full[2].copy(completed = true, completedByUid = "owner")
        assertTrue(missingSharedScheduleRequests(snapshot(full), "owner", day).isEmpty())
    }

    @Test fun offlineRevokedAndUnconfiguredTasksDoNotProduceRequests() {
        assertTrue(missingSharedScheduleRequests(snapshot().copy(mayBeStale = true), "owner", day).isEmpty())
        assertTrue(missingSharedScheduleRequests(snapshot(), "other", day).isEmpty())
        val staleAssignment = chore.copy(schedule = rule.copy(memberUids = listOf("former-member")))
        assertTrue(missingSharedScheduleRequests(snapshot().copy(chores = listOf(staleAssignment)), "owner", day).isEmpty())
        assertTrue(missingSharedScheduleRequests(snapshot().copy(chores = listOf(chore.copy(schedule = null))), "owner", day).isEmpty())
    }
}
