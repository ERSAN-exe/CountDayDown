package com.Zero23.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.Zero23.countdown.R
import com.Zero23.countdown.data.DataManager
import com.Zero23.countdown.ui.theme.CountDownTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        setContent {
            val dataManager = remember { DataManager(this) }
            val themeMode by dataManager.themeMode.collectAsState(initial = 0)
            val themeColorHex by dataManager.themeColor.collectAsState(initial = null)
            val appBgImage by dataManager.appBackgroundImage.collectAsState(initial = null)
            val appBgBrightness by dataManager.appBackgroundBrightness.collectAsState(initial = 0.5f)
            
            val isDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val customThemeColor = themeColorHex?.let { 
                try { Color(it.toColorInt()) } catch(_: Exception) { null }
            }

            CountDownTheme(darkTheme = isDarkTheme, customColor = customThemeColor) {
                val events by dataManager.events.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            finish()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Custom Background Image Support
                    if (appBgImage != null) {
                        AsyncImage(
                            model = appBgImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isDarkTheme) Color.Black.copy(alpha = appBgBrightness)
                                    else Color.White.copy(alpha = appBgBrightness)
                                )
                        )
                    } else {
                        // If no image, we can add a slight dim or tint to distinguish the dialog better
                        // but sticking to user's request: "随着亮色暗色主题适当调整颜色"
                        // background is already set on the root Box.
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .wrapContentHeight()
                            .clickable(enabled = false) {}, // Prevent click propagation
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.choose_event_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(events) { event ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    dataManager.setWidgetEvent(appWidgetId, event.id)

                                                    // Trigger update for both providers
                                                    val intent1 = Intent(this@WidgetConfigActivity, CountdownWidgetProvider::class.java)
                                                    intent1.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                                    intent1.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                                    sendBroadcast(intent1)

                                                    val intent2 = Intent(this@WidgetConfigActivity, CountdownWidgetProvider4x2::class.java)
                                                    intent2.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                                    intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                                    sendBroadcast(intent2)

                                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                                    finish()
                                                }
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ) {
                                        Text(
                                            text = event.name,
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
