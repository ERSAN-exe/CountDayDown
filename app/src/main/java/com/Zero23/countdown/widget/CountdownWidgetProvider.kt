package com.Zero23.countdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
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
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class CountdownWidgetProvider : AppWidgetProvider() {
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
        if (intent.action == Intent.ACTION_LOCALE_CHANGED || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, CountdownWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, event: CountdownEvent?) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val customTypeface = if (event?.customFontPath != null) {
            try { Typeface.createFromFile(event.customFontPath) } catch (_: Exception) { null }
        } else null
        
        if (event != null) {
            val customColor = event.colorHex?.let { try { it.toColorInt() } catch(_: Exception) { null } }
            val textColor = if (event.widgetImageUri != null) (customColor ?: Color.WHITE) else Color.WHITE

            // Background Image logic (2x2 uses widgetImageUri)
            if (event.widgetImageUri != null) {
                try {
                    val uri = event.widgetImageUri.toUri()
                    val bitmap = decodeSampledBitmapFromUri(context, uri, 400, 400)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_background_image, bitmap)
                        views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                        
                        views.setViewVisibility(R.id.widget_background_overlay, View.VISIBLE)
                        val alpha = (255 * event.backgroundBrightness).toInt().coerceIn(0, 255)
                        val maskColor = (alpha shl 24) or 0x00000000
                        views.setInt(R.id.widget_background_overlay, "setBackgroundColor", maskColor)
                        
                        views.setInt(R.id.widget_layout_root, "setBackgroundColor", Color.TRANSPARENT)
                        views.setInt(R.id.widget_edit_btn, "setColorFilter", textColor)
                    } else {
                        views.setViewVisibility(R.id.widget_background_image, View.GONE)
                        views.setViewVisibility(R.id.widget_background_overlay, View.GONE)
                        applyBaseStyle(views, event)
                    }
                } catch (_: Exception) {
                    views.setViewVisibility(R.id.widget_background_image, View.GONE)
                    views.setViewVisibility(R.id.widget_background_overlay, View.GONE)
                    applyBaseStyle(views, event)
                }
            } else {
                views.setViewVisibility(R.id.widget_background_image, View.GONE)
                views.setViewVisibility(R.id.widget_background_overlay, View.GONE)
                applyBaseStyle(views, event)
            }

            val now = LocalDateTime.now()
            val target = event.calculateTarget(now)

            val days = ChronoUnit.DAYS.between(now, target)
            val hours = ChronoUnit.HOURS.between(now.plusDays(days), target)
            val minutes = ChronoUnit.MINUTES.between(now.plusDays(days).plusHours(hours), target)
            
            val isPast = days < 0 || (days == 0L && hours < 0) || (days == 0L && hours == 0L && minutes < 0)
            
            val actualTextColor = if (event.widgetImageUri != null) textColor else Color.WHITE
            val secondaryTextColor = if (event.widgetImageUri != null) textColor else "#99000000".toColorInt()

            setWidgetText(views, R.id.widget_title, R.id.widget_title_img, event.name, 20f, actualTextColor, customTypeface, context)
            
            if (isPast) {
                val pastDays = days.absoluteValue
                val pastHours = hours.absoluteValue
                val pastMinutes = minutes.absoluteValue
                
                val ago = context.getString(R.string.ago_suffix)
                val daysValue = if (pastDays > 0) pastDays.toString() else (if (pastHours > 0) pastHours.toString() else pastMinutes.toString())
                val daysUnit = if (pastDays > 0) context.getString(R.string.unit_days) + ago else (if (pastHours > 0) context.getString(R.string.unit_hours) + ago else context.getString(R.string.unit_minutes) + ago)
                
                setWidgetText(views, R.id.widget_days, R.id.widget_days_img, daysValue, 34f, secondaryTextColor, customTypeface, context)
                setWidgetText(views, R.id.widget_days_unit, R.id.widget_days_unit_img, daysUnit, 16f, secondaryTextColor, customTypeface, context)
                views.setViewVisibility(R.id.widget_time, View.GONE)
                views.setViewVisibility(R.id.widget_time_img, View.GONE)
            } else {
                if (days > 0) {
                    setWidgetText(views, R.id.widget_days, R.id.widget_days_img, days.toString(), 34f, secondaryTextColor, customTypeface, context)
                    setWidgetText(views, R.id.widget_days_unit, R.id.widget_days_unit_img, context.getString(R.string.unit_days), 16f, secondaryTextColor, customTypeface, context)
                    views.setViewVisibility(R.id.widget_time, View.GONE)
                    views.setViewVisibility(R.id.widget_time_img, View.GONE)
                } else if (hours > 0) {
                    setWidgetText(views, R.id.widget_days, R.id.widget_days_img, hours.toString(), 34f, secondaryTextColor, customTypeface, context)
                    setWidgetText(views, R.id.widget_days_unit, R.id.widget_days_unit_img, context.getString(R.string.unit_hours), 16f, secondaryTextColor, customTypeface, context)
                    views.setViewVisibility(R.id.widget_time, View.GONE)
                    views.setViewVisibility(R.id.widget_time_img, View.GONE)
                } else {
                    setWidgetText(views, R.id.widget_days, R.id.widget_days_img, minutes.toString(), 34f, secondaryTextColor, customTypeface, context)
                    setWidgetText(views, R.id.widget_days_unit, R.id.widget_days_unit_img, context.getString(R.string.unit_minutes), 16f, secondaryTextColor, customTypeface, context)
                    views.setViewVisibility(R.id.widget_time, View.GONE)
                    views.setViewVisibility(R.id.widget_time_img, View.GONE)
                }
            }
        } else {
            views.setViewVisibility(R.id.widget_background_image, View.GONE)
            views.setViewVisibility(R.id.widget_background_overlay, View.GONE)
            setWidgetText(views, R.id.widget_title, R.id.widget_title_img, context.getString(R.string.widget_no_activity), 20f, Color.WHITE, null, context)
            setWidgetText(views, R.id.widget_days, R.id.widget_days_img, context.getString(R.string.widget_click_create), 34f, Color.WHITE, null, context)
            setWidgetText(views, R.id.widget_days_unit, R.id.widget_days_unit_img, "", 16f, Color.WHITE, null, context)
            setWidgetText(views, R.id.widget_time, R.id.widget_time_img, "", 13f, Color.WHITE, null, context)
            setWidgetText(views, R.id.widget_target_label, 0, "", 12f, Color.WHITE, null, context)
            views.setInt(R.id.widget_layout_root, "setBackgroundColor", Color.DKGRAY)
            views.setInt(R.id.widget_edit_btn, "setColorFilter", Color.WHITE)

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
        views.setInt(R.id.widget_edit_btn, "setColorFilter", Color.WHITE)
    }

    private fun setWidgetText(views: RemoteViews, viewId: Int, imgViewId: Int, text: String, fontSizeSp: Float, color: Int, typeface: Typeface?, context: Context) {
        if (typeface != null) {
            views.setViewVisibility(viewId, View.GONE)
            if (imgViewId != 0) {
                views.setViewVisibility(imgViewId, View.VISIBLE)
                val bitmap = createTextBitmap(text, fontSizeSp, color, typeface, context)
                views.setImageViewBitmap(imgViewId, bitmap)
            }
        } else {
            views.setViewVisibility(viewId, View.VISIBLE)
            if (imgViewId != 0) views.setViewVisibility(imgViewId, View.GONE)
            views.setTextViewText(viewId, text)
            views.setTextColor(viewId, color)
        }
    }

    private fun createTextBitmap(text: String, fontSizeSp: Float, color: Int, typeface: Typeface, context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = color
            this.textSize = fontSizeSp * density
        }
        val fontMetrics = paint.fontMetrics
        val width = paint.measureText(text).toInt().coerceAtLeast(1)
        val height = (fontMetrics.bottom - fontMetrics.top).toInt().coerceAtLeast(1)
        
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, -fontMetrics.top, paint)
        return bitmap
    }

    @Suppress("SameParameterValue")
    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: Exception) { null }
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
