package com.hebi.homecrew.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HomeCrewReminderTimeZoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        try {
            HomeCrewReminderScheduler.apply(context) { succeeded ->
                try {
                    if (!succeeded) android.util.Log.w("HomeCrewReminder", "Reminder schedule refresh did not complete")
                } finally {
                    pending.finish()
                }
            }
        } catch (error: Exception) {
            pending.finish()
            android.util.Log.w("HomeCrewReminder", "Unable to refresh local reminder schedule", error)
        }
    }
}
