package com.hebi.homecrew.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
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
    fun apply(
        context: Context,
        reschedule: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val app = context.applicationContext
        val preferences = HomeCrewReminderPreferences(app)
        val settings = preferences.read()
        val manager = WorkManager.getInstance(app)
        val requestGeneration = ++generation
        if (!settings.enabled) {
            preferences.rememberScheduledZoneKey(null)
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
            val operation = manager.cancelUniqueWork(WORK_NAME)
            if (onComplete != null) observeCompletion(operation, onComplete)
            return
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
        observeCompletion(operation) { succeeded ->
            synchronized(this@HomeCrewReminderScheduler) {
                if (requestGeneration == generation && succeeded &&
                    preferences.read() == settings &&
                    reminderScheduleKey(ZonedDateTime.now()) == scheduleKey
                ) {
                    preferences.rememberScheduledZoneKey(scheduleKey)
                }
            }
            onComplete?.invoke(succeeded)
        }
    }

    private fun observeCompletion(operation: Operation, callback: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val state = operation.state
            var finished = false
            lateinit var observer: Observer<Operation.State>
            lateinit var timeout: Runnable

            fun finish(success: Boolean) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeout)
                state.removeObserver(observer)
                callback(success)
            }

            observer = Observer { value ->
                when (value) {
                    is Operation.State.SUCCESS -> finish(true)
                    is Operation.State.FAILURE -> finish(false)
                    else -> Unit
                }
            }
            timeout = Runnable { finish(false) }
            handler.postDelayed(timeout, 8_000)
            try {
                state.observeForever(observer)
            } catch (_: Exception) {
                finish(false)
            }
        }
    }
}
