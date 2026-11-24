package com.hebi.homecrew.reminder

import android.content.Context

internal class HomeCrewReminderPreferences(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(
        "homecrew_reminder_v1", Context.MODE_PRIVATE
    )

    fun read(): HomeCrewReminderSettings {
        val hour = storage.getInt("hour", 20).coerceIn(0, 23)
        val minute = storage.getInt("minute", 0).coerceIn(0, 59)
        return HomeCrewReminderSettings(
            enabled = storage.getBoolean("enabled", false),
            time = HomeCrewReminderTime(hour, minute)
        )
    }

    fun updateEnabled(value: Boolean): HomeCrewReminderSettings {
        check(storage.edit().putBoolean("enabled", value).commit())
        return read()
    }

    fun updateTime(value: HomeCrewReminderTime): HomeCrewReminderSettings {
        check(storage.edit().putInt("hour", value.hour).putInt("minute", value.minute).commit())
        return read()
    }

    fun scheduledZoneKey(): String? = storage.getString("scheduledZoneKey", null)

    fun rememberScheduledZoneKey(value: String?) {
        val edit = storage.edit()
        if (value == null) edit.remove("scheduledZoneKey")
        else edit.putString("scheduledZoneKey", value)
        edit.apply()
    }

    fun alreadyDelivered(todayEpochDay: Long): Boolean =
        storage.getLong("lastDeliveredEpochDay", Long.MIN_VALUE) == todayEpochDay

    fun markDelivered(todayEpochDay: Long) {
        storage.edit().putLong("lastDeliveredEpochDay", todayEpochDay).apply()
    }
}
