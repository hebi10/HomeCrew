package com.hebi.homecrew.data.cloud

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewRemoteScheduleTest {
    private val rule = RemoteSchedule(
        LocalDate.of(2026, 11, 25).toEpochDay(), "WEEKLY", listOf(1, 3), 2,
        "BALANCED", listOf("alice", "bob")
    )

    @Test fun payloadMatchesServerAndPreservesParticipantOrder() {
        assertEquals("BALANCED", rule.payload()["assignmentMode"])
        assertEquals(listOf("alice", "bob"), rule.payload()["memberUids"])
        assertEquals(listOf(1, 3), rule.payload()["weekdays"])
        assertEquals(rule.startEpochDay, rule.payload()["startEpochDay"])
    }

    @Test fun invalidRulesAreRejectedBeforeSending() {
        assertThrows(IllegalArgumentException::class.java) { rule.copy(weekdays = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { rule.copy(memberUids = listOf("alice", "alice")) }
        assertThrows(IllegalArgumentException::class.java) { rule.copy(assignmentMode = "FIXED") }
        assertThrows(IllegalArgumentException::class.java) { rule.copy(intervalDays = 31) }
        assertThrows(IllegalArgumentException::class.java) { rule.copy(memberUids = listOf("../bad")) }
    }
}
