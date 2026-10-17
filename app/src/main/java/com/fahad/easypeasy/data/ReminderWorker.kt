package com.fahad.easypeasy.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.fahad.easypeasy.R
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("practice", "Practice reminders", NotificationManager.IMPORTANCE_DEFAULT))
        notifications.notify(21, NotificationCompat.Builder(applicationContext, "practice").setSmallIcon(R.drawable.mascot).setContentTitle(applicationContext.getString(R.string.app_name)).setContentText(applicationContext.getString(R.string.reminder_body)).setAutoCancel(true).build())
        return Result.success()
    }
    companion object {
        fun schedule(context: Context, hour: Int, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) { work.cancelUniqueWork("daily-reminder"); return }
            val now = ZonedDateTime.now()
            var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build()
            work.enqueueUniquePeriodicWork("daily-reminder", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
