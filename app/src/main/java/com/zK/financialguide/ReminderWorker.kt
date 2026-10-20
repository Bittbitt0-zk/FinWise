package com.zK.financialguide

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// External library used here: WorkManager (androidx.work) schedules the reminder in the background.

const val REMINDER_WORK_NAME = "monthly_reminder"
private const val CHANNEL_ID = "reminders"
private const val NOTIFICATION_ID = 1

/** Runs in the background and shows the monthly check-in notification. */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        showReminder(applicationContext)
        return Result.success()
    }
}

/** Android 13+ needs the user's permission to show notifications. */
fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

fun showReminder(context: Context) {
    if (!hasNotificationPermission(context)) return

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Monthly check-in", NotificationManager.IMPORTANCE_DEFAULT)
    )

    // Tapping the notification opens the app
    val openApp = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Time for your monthly money check-in")
        .setContentText("Update your savings, record this month's spending and review your plan.")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("Update your savings, record this month's spending and review your plan.")
        )
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()

    manager.notify(NOTIFICATION_ID, notification)
}

fun scheduleReminder(context: Context) {
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.DAYS)
        .setInitialDelay(30, TimeUnit.DAYS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
    )
}

fun cancelReminder(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
}