package com.hebi.homecrew.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal object HomeCrewReminderScheduler {
    private const val WORK_NAME = "homecrew.daily_local_reminder.v1"
    const val NOTIFICATION_ID = 26011
    private var generation = 0L

    @Synchronized
    fun apply(context: Context, reschedule: Boolean = false): Operation {
        val app = context.applicationContext
        val preferences = HomeCrewReminderPreferences(app)
        val settings = preferences.read()
        val manager = WorkManager.getInstance(app)
        val requestGeneration = ++generation
        if (!settings.enabled) {
            preferences.rememberScheduledZoneKey(null)
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
            return manager.cancelUniqueWork(WORK_NAME)
        }

        val now = ZonedDateTime.now()
        val scheduleKey = reminderScheduleKey(now)
        val reanchor = shouldReanchorReminder(preferences.scheduledZoneKey(), now, reschedule)
        val delay = nextReminderDelay(now, settings.time).toMillis()
        val request = PeriodicWorkRequestBuilder<HomeCrewDailySummaryWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()

        val operation = manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (reanchor) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        operation.result.addListener({
            synchronized(this@HomeCrewReminderScheduler) {
                if (requestGeneration == generation &&
                    runCatching { operation.result.get() }.isSuccess &&
                    preferences.read() == settings &&
                    reminderScheduleKey(ZonedDateTime.now()) == scheduleKey
                ) {
                    preferences.rememberScheduledZoneKey(scheduleKey)
                }
            }
        }, ContextCompat.getMainExecutor(app))
        return operation
    }
}
