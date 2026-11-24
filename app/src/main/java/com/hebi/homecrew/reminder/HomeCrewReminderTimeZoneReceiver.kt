package com.hebi.homecrew.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class HomeCrewReminderTimeZoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        try {
            val operation = HomeCrewReminderScheduler.apply(context)
            operation.result.addListener(
                { pending.finish() },
                ContextCompat.getMainExecutor(context)
            )
        } catch (error: Exception) {
            pending.finish()
            android.util.Log.w("HomeCrewReminder", "Unable to refresh local reminder schedule", error)
        }
    }
}
