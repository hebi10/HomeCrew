package com.hebi.homecrew.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal object HomeCrewReminderScheduler {
    private const val WORK_NAME = "homecrew.daily_local_reminder.v1"
    const val NOTIFICATION_ID = 26011

    fun apply(context: Context, reschedule: Boolean = false) {
        val app = context.applicationContext
        val settings = HomeCrewReminderPreferences(app).read()
        val manager = WorkManager.getInstance(app)
        if (!settings.enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
            return
        }
        val delay = nextReminderDelay(ZonedDateTime.now(), settings.time).toMillis()
        val request = PeriodicWorkRequestBuilder<HomeCrewDailySummaryWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
