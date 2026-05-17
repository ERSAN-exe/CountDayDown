package com.Zero23.countdown.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: applicationContext.getString(com.Zero23.countdown.R.string.notif_title)
        val content = inputData.getString("content") ?: applicationContext.getString(com.Zero23.countdown.R.string.notif_default_content)
        val eventId = inputData.getString("eventId") ?: "0"

        showNotification(title, content, eventId)
        return Result.success()
    }

    private fun showNotification(title: String, content: String, eventId: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "countdown_reminders"

        // Min SDK is 26, so no need for check, but channel creation is safe
        val channel = NotificationChannel(channelId, applicationContext.getString(com.Zero23.countdown.R.string.notif_title), NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(eventId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}
