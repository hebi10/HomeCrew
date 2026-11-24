package com.hebi.homecrew.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hebi.homecrew.MainActivity
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException

internal class HomeCrewDailySummaryWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = HomeCrewReminderPreferences(context)
        val settings = preferences.read()
        if (!settings.enabled) return Result.success()
        if (LocalHouseholdStore(context).load() == null) return Result.success()
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        if (preferences.alreadyDelivered(today.toEpochDay())) return Result.success()
        if (!notificationsAllowed(context)) return Result.success()
        // A periodic run may still be anchored to an earlier zone or DST offset.
        // Do not deliver or mark today complete before the selected local time.
        if (!reminderTimeReached(now, settings.time)) return Result.retry()

        return try {
            val repository = HomeCrewRoomRepository(HomeCrewDatabase.get(context).chores())
            repository.extendUpcoming(today)
            val snapshot = repository.snapshot()
            val remaining = snapshot.chores.count {
                it.date == today && !it.completed && !it.skipped
            }
            val latestSettings = preferences.read()
            val deliveryTime = ZonedDateTime.now()
            if (isStopped || !latestSettings.enabled || !notificationsAllowed(context)) return Result.success()
            if (deliveryTime.toLocalDate() != today ||
                !reminderTimeReached(deliveryTime, latestSettings.time)
            ) return Result.retry()
            if (preferences.alreadyDelivered(today.toEpochDay())) return Result.success()
            if (remaining > 0) {
                showNotification(context, remaining)
            } else {
                NotificationManagerCompat.from(context).cancel(HomeCrewReminderScheduler.NOTIFICATION_ID)
            }
            preferences.markDelivered(today.toEpochDay())
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, remaining: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "homecrew_daily_chores", "오늘의 집안일", NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "homecrew_daily_chores")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("HomeCrew · 오늘의 집안일")
            .setContentText("오늘 남은 집안일 ${remaining}건")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(
            HomeCrewReminderScheduler.NOTIFICATION_ID, notification
        )
    }
}
