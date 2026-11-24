package com.hebi.homecrew.reminder

import java.time.ZonedDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewReminderTimeTest {
    private val timeZone = ZoneId.of("Asia/Seoul")

    @Test fun settingsTimeValidationAnd24HourStorage() {
        assertEquals("20:05", HomeCrewReminderTime.parse("20:05").toString())
        assertNull(HomeCrewReminderTime.parse("24:00"))
        assertNull(HomeCrewReminderTime.parse("20:60"))
        assertNull(HomeCrewReminderTime.parse("7:00"))
        assertNull(HomeCrewReminderTime.parse(""))
    }

    @Test fun scheduleTargetsTodayBeforeReminderAndTomorrowAfterReminder() {
        val time = HomeCrewReminderTime(20, 0)
        val before = ZonedDateTime.of(2026, 11, 17, 19, 0, 0, 0, timeZone)
        val after = ZonedDateTime.of(2026, 11, 17, 20, 0, 0, 0, timeZone)
        assertEquals(60L, nextReminderDelay(before, time).toMinutes())
        assertEquals(1440L, nextReminderDelay(after, time).toMinutes())
        val changedTime = HomeCrewReminderTime(21, 30)
        val midway = ZonedDateTime.of(2026, 11, 17, 19, 30, 0, 0, timeZone)
        assertEquals(120L, nextReminderDelay(midway, changedTime).toMinutes())
    }

    @Test fun sameZoneKeepsExistingWorkAndChangedZoneReanchors() {
        val seoul = ZonedDateTime.of(2026, 11, 17, 19, 0, 0, 0, timeZone)
        val london = seoul.withZoneSameInstant(ZoneId.of("Europe/London"))
        val originalKey = reminderScheduleKey(seoul)
        assertEquals(false, shouldReanchorReminder(originalKey, seoul.plusMinutes(30), false))
        assertTrue(shouldReanchorReminder(originalKey, seoul, true))
        assertTrue(shouldReanchorReminder(null, seoul, false))
        assertTrue(shouldReanchorReminder(originalKey, london, false))
        assertEquals(600L, nextReminderDelay(london, HomeCrewReminderTime(20, 0)).toMinutes())
    }

    @Test fun changedDaylightSavingOffsetReanchorsAndEarlyRunDoesNotDeliver() {
        val zone = ZoneId.of("America/New_York")
        val before = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, zone)
        val after = ZonedDateTime.of(2026, 11, 1, 19, 0, 0, 0, zone)
        val selected = HomeCrewReminderTime(20, 0)
        assertTrue(shouldReanchorReminder(reminderScheduleKey(before), after, false))
        assertEquals(false, reminderTimeReached(after, selected))
        assertTrue(reminderTimeReached(after.plusHours(1), selected))
    }

    @Test fun timezoneTransitionDoesNotScheduleInPast() {
        val z = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, ZoneId.of("America/New_York"))
        assertTrue(nextReminderDelay(z, HomeCrewReminderTime(2, 30)).toMillis() > 0)
    }
}
