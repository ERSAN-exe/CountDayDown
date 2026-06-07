package com.Zero23.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.Zero23.countdown.R
import com.Zero23.countdown.data.DataManager
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
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
            MaterialTheme {
                val dataManager = remember { DataManager(this) }
                val events by dataManager.events.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()

                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.choose_event_title)) }) }
                ) { padding ->
                    LazyColumn(modifier = Modifier.padding(padding)) {
                        items(events) { event ->
                            ListItem(
                                headlineContent = { Text(event.name) },
                                modifier = Modifier.clickable {
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
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
