package com.Zero23.countdown.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.Zero23.countdown.data.CountdownEvent
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object NotificationHelper {

    fun scheduleNotification(context: Context, event: CountdownEvent) {
        if (event.reminderMinutesBefore == null) return

        val workManager = WorkManager.getInstance(context)
        
        // Cancel existing work for this event
        workManager.cancelAllWorkByTag(event.id)

        val targetDateTime = LocalDateTime.parse(event.targetDateTime)
        
        // Calculate the actual target time, handling past recurring events
        var nextTargetDateTime = targetDateTime
        val now = LocalDateTime.now()
        
        if (event.repeatType != null && event.repeatType != "none") {
            while (nextTargetDateTime.isBefore(now)) {
                nextTargetDateTime = when (event.repeatType) {
                    "daily" -> nextTargetDateTime.plusDays(1)
                    "weekly" -> nextTargetDateTime.plusWeeks(1)
                    "monthly" -> nextTargetDateTime.plusMonths(1)
                    "yearly" -> nextTargetDateTime.plusYears(1)
                    "custom" -> {
                        val interval = event.repeatInterval?.toLong() ?: 1L
                        when (event.repeatUnit) {
                            "seconds" -> nextTargetDateTime.plusSeconds(interval)
                            "minutes" -> nextTargetDateTime.plusMinutes(interval)
                            "hours" -> nextTargetDateTime.plusHours(interval)
                            "days" -> nextTargetDateTime.plusDays(interval)
                            "weeks" -> nextTargetDateTime.plusWeeks(interval)
                            "months" -> nextTargetDateTime.plusMonths(interval)
                            "years" -> nextTargetDateTime.plusYears(interval)
                            else -> nextTargetDateTime.plusDays(interval)
                        }
                    }
                    else -> nextTargetDateTime
                }
            }
        }

        val reminderDateTime = nextTargetDateTime.minusMinutes(event.reminderMinutesBefore.toLong())
        val delay = Duration.between(LocalDateTime.now(), reminderDateTime)

        if (delay.isNegative) return // Already passed

        // Dynamic content generation
        val displayContent = if (!event.notificationContent.isNullOrBlank()) {
            event.notificationContent
        } else {
            val timeStr = when (event.reminderMinutesBefore) {
                0 -> context.getString(com.Zero23.countdown.R.string.notif_now)
                5 -> context.getString(com.Zero23.countdown.R.string.notif_5m)
                10 -> context.getString(com.Zero23.countdown.R.string.notif_10m)
                30 -> context.getString(com.Zero23.countdown.R.string.notif_30m)
                60 -> context.getString(com.Zero23.countdown.R.string.notif_1h)
                1440 -> context.getString(com.Zero23.countdown.R.string.notif_1d)
                else -> context.getString(com.Zero23.countdown.R.string.notif_soon)
            }
            context.getString(com.Zero23.countdown.R.string.notif_format, event.name, timeStr)
        }

        val inputData = Data.Builder()
            .putString("title", context.getString(com.Zero23.countdown.R.string.notif_title))
            .putString("content", displayContent)
            .putString("eventId", event.id)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(event.id)
            .build()

        workManager.enqueue(workRequest)
    }

    fun cancelNotification(context: Context, eventId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(eventId)
    }
}
