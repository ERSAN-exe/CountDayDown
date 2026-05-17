package com.Zero23.countdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.Zero23.countdown.MainActivity
import com.Zero23.countdown.R
import com.Zero23.countdown.data.CountdownEvent
import com.Zero23.countdown.data.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class CountdownWidgetProvider4x2 : AppWidgetProvider() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dataManager = DataManager(context)
        scope.launch {
            val events = dataManager.events.first()
            
            for (appWidgetId in appWidgetIds) {
                val eventId = dataManager.getWidgetEvent(appWidgetId).first()
                val event = events.find { it.id == eventId } ?: events.firstOrNull()
                updateWidget(context, appWidgetManager, appWidgetId, event)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, CountdownWidgetProvider4x2::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, event: CountdownEvent?) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout_4x2)
        
        if (event != null) {
            // Background Image logic
            if (event.backgroundImageUri != null) {
                try {
                    val uri = event.backgroundImageUri.toUri()
                    // Scale image to a reasonable size for widget to avoid Binder limit
                    val bitmap = decodeSampledBitmapFromUri(context, uri, 800, 400)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_background_image, bitmap)
                        views.setViewVisibility(R.id.widget_background_image, android.view.View.VISIBLE)
                        
                        // Mask/Overlay for brightness
                        views.setViewVisibility(R.id.widget_background_overlay, android.view.View.VISIBLE)
                        val alpha = (255 * (1f - event.backgroundBrightness)).toInt().coerceIn(0, 255)
                        val maskColor = (alpha shl 24) or 0x00000000
                        views.setInt(R.id.widget_background_overlay, "setBackgroundColor", maskColor)
                        
                        // Set layout root to transparent so image shows through
                        views.setInt(R.id.widget_layout_root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                        
                        // Text colors for image background
                        val customColor = event.colorHex?.let { try { it.toColorInt() } catch(_: Exception) { null } }
                        val textColor = customColor ?: android.graphics.Color.WHITE
                        views.setTextColor(R.id.widget_title, textColor)
                        views.setTextColor(R.id.widget_days, textColor)
                        views.setTextColor(R.id.widget_days_unit, textColor)
                        views.setTextColor(R.id.widget_time, textColor)
                        views.setTextColor(R.id.widget_target_label, (textColor and 0x00FFFFFF) or 0xB3000000.toInt())
                        views.setInt(R.id.widget_edit_btn, "setColorFilter", textColor)
                    } else {
                        views.setViewVisibility(R.id.widget_background_image, android.view.View.GONE)
                        views.setViewVisibility(R.id.widget_background_overlay, android.view.View.GONE)
                        applyBaseStyle(views, event)
                    }
                } catch (_: Exception) {
                    views.setViewVisibility(R.id.widget_background_image, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_background_overlay, android.view.View.GONE)
                    applyBaseStyle(views, event)
                }
            } else {
                views.setViewVisibility(R.id.widget_background_image, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_background_overlay, android.view.View.GONE)
                applyBaseStyle(views, event)
            }

            val target = LocalDateTime.parse(event.targetDateTime)
            val now = LocalDateTime.now()

            val days = ChronoUnit.DAYS.between(now, target)
            val hours = ChronoUnit.HOURS.between(now.plusDays(days), target)
            val minutes = ChronoUnit.MINUTES.between(now.plusDays(days).plusHours(hours), target)
            
            val isPast = days < 0 || (days == 0L && hours < 0) || (days == 0L && hours == 0L && minutes < 0)
            
            views.setTextViewText(R.id.widget_title, event.name)
            views.setTextViewText(R.id.widget_target_label, "${context.getString(R.string.target)}${target.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            
            if (isPast) {
                val pastDays = days.absoluteValue
                val pastHours = hours.absoluteValue
                val pastMinutes = minutes.absoluteValue
                
                val ago = context.getString(R.string.ago_suffix)
                views.setTextViewText(R.id.widget_days, if (pastDays > 0) pastDays.toString() else (if (pastHours > 0) pastHours.toString() else pastMinutes.toString()))
                views.setTextViewText(R.id.widget_days_unit, if (pastDays > 0) context.getString(R.string.unit_days) + ago else (if (pastHours > 0) context.getString(R.string.unit_hours) + ago else context.getString(R.string.unit_minutes) + ago))
                views.setTextViewText(R.id.widget_time, "")
            } else {
                views.setTextViewText(R.id.widget_days, days.toString())
                views.setTextViewText(R.id.widget_days_unit, context.getString(R.string.unit_days))
                views.setTextViewText(R.id.widget_time, "${hours}${context.getString(R.string.unit_hours)} ${minutes}${context.getString(R.string.unit_minutes)}")
            }
        } else {
            views.setViewVisibility(R.id.widget_background_image, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_background_overlay, android.view.View.GONE)
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_activity))
            views.setTextViewText(R.id.widget_days, context.getString(R.string.widget_click_create))
            views.setTextViewText(R.id.widget_days_unit, "")
            views.setTextViewText(R.id.widget_time, "")
            views.setTextViewText(R.id.widget_target_label, "")
            views.setInt(R.id.widget_layout_root, "setBackgroundColor", android.graphics.Color.DKGRAY)
            views.setTextColor(R.id.widget_title, android.graphics.Color.WHITE)
            views.setTextColor(R.id.widget_days, android.graphics.Color.WHITE)
            views.setInt(R.id.widget_edit_btn, "setColorFilter", android.graphics.Color.WHITE)

            val createIntent = Intent(context, MainActivity::class.java)
            createIntent.action = "com.Zero23.countdown.ACTION_CREATE_EVENT"
            val createPendingIntent = PendingIntent.getActivity(
                context,
                0,
                createIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_layout_root, createPendingIntent)
        }

        // Add edit button click listener
        val configIntent = Intent(context, WidgetConfigActivity::class.java)
        configIntent.action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_edit_btn, pendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun applyBaseStyle(views: RemoteViews, event: CountdownEvent) {
        val colorHex = event.colorHex ?: "#FF4500" // Default color
        views.setInt(R.id.widget_layout_root, "setBackgroundColor", colorHex.toColorInt())
        views.setTextColor(R.id.widget_title, android.graphics.Color.WHITE)
        views.setTextColor(R.id.widget_days, "#99000000".toColorInt())
        views.setTextColor(R.id.widget_days_unit, "#99000000".toColorInt())
        views.setTextColor(R.id.widget_time, "#99000000".toColorInt())
        views.setTextColor(R.id.widget_target_label, "#B3FFFFFF".toColorInt())
        views.setInt(R.id.widget_edit_btn, "setColorFilter", android.graphics.Color.WHITE)
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
