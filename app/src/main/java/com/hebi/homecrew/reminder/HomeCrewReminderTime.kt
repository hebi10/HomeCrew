package com.hebi.homecrew.reminder

import java.time.Duration
import java.time.ZonedDateTime

internal data class HomeCrewReminderTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }

    override fun toString(): String = "%02d:%02d".format(java.util.Locale.ROOT, hour, minute)

    companion object {
        fun parse(text: String): HomeCrewReminderTime? {
            val value = text.trim()
            if (!Regex("""\d{2}:\d{2}""").matches(value)) return null
            val numbers = value.split(":")
            return runCatching {
                HomeCrewReminderTime(numbers[0].toInt(), numbers[1].toInt())
            }.getOrNull()
        }
    }
}

internal data class HomeCrewReminderSettings(
    val enabled: Boolean,
    val time: HomeCrewReminderTime
)

internal fun nextReminderDelay(now: ZonedDateTime, time: HomeCrewReminderTime): Duration {
    val today = now.toLocalDate().atTime(time.hour, time.minute).atZone(now.zone)
    val scheduled = if (today.isAfter(now)) today
        else now.toLocalDate().plusDays(1).atTime(time.hour, time.minute).atZone(now.zone)
    return Duration.between(now, scheduled).coerceAtLeast(Duration.ofSeconds(1))
}

// Zone and offset are both significant: travel and daylight-saving changes can
// move an existing 24-hour WorkManager schedule away from the local wall clock.
internal fun reminderScheduleKey(now: ZonedDateTime): String =
    "${now.zone.id}|${now.offset.id}"

internal fun shouldReanchorReminder(
    previousScheduleKey: String?,
    now: ZonedDateTime,
    explicitlyRescheduled: Boolean
): Boolean = explicitlyRescheduled || previousScheduleKey != reminderScheduleKey(now)

internal fun reminderTimeReached(now: ZonedDateTime, time: HomeCrewReminderTime): Boolean =
    !now.isBefore(now.toLocalDate().atTime(time.hour, time.minute).atZone(now.zone))
