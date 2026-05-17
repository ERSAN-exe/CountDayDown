package com.Zero23.countdown.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class CountdownEvent(
    val id: String,
    val name: String,
    val targetDateTime: String, // ISO-8601 LocalDateTime
    val colorHex: String? = null, // Store custom color as HEX string
    val notificationContent: String? = null,
    val reminderMinutesBefore: Int? = null, // Minutes before target to notify
    val repeatType: String? = "none", // none, daily, weekly, monthly, yearly, custom
    val repeatInterval: Int? = null,
    val repeatUnit: String? = null, // days, weeks, months, years
    val backgroundImageUri: String? = null,
    val widgetImageUri: String? = null,
    val backgroundBrightness: Float = 0.5f, // 0.0 to 1.0, 1.0 means no mask, 0.0 means black
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class BackupData(
    val events: List<CountdownEvent>,
    val themeMode: Int,
    val themeColor: String?,
    val notificationsEnabled: Boolean
)

class DataManager(private val context: Context) {
    private val EVENTS_KEY = stringPreferencesKey("events_list")
    private val THEME_MODE_KEY = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
    private val THEME_COLOR_KEY = stringPreferencesKey("theme_color") // Hex color or null for default
    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val WIDGET_CONFIG_KEY = stringPreferencesKey("widget_config") // JSON map: widgetId (String) -> eventId (String)

    val events: Flow<List<CountdownEvent>> = context.dataStore.data.map { preferences ->
        val json = preferences[EVENTS_KEY] ?: "[]"
        try {
            Json.decodeFromString<List<CountdownEvent>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: 0
    }

    val themeColor: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[THEME_COLOR_KEY]
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
    }

    suspend fun saveEvents(events: List<CountdownEvent>) {
        context.dataStore.edit { preferences ->
            preferences[EVENTS_KEY] = Json.encodeToString(events)
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun setThemeColor(colorHex: String?) {
        context.dataStore.edit { preferences ->
            if (colorHex == null) preferences.remove(THEME_COLOR_KEY)
            else preferences[THEME_COLOR_KEY] = colorHex
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setWidgetEvent(widgetId: Int, eventId: String?) {
        context.dataStore.edit { preferences ->
            val json = preferences[WIDGET_CONFIG_KEY] ?: "{}"
            val map = Json.decodeFromString<MutableMap<String, String>>(json)
            if (eventId == null) map.remove(widgetId.toString())
            else map[widgetId.toString()] = eventId
            preferences[WIDGET_CONFIG_KEY] = Json.encodeToString(map)
        }
    }

    fun getWidgetEvent(widgetId: Int): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[WIDGET_CONFIG_KEY] ?: "{}"
            val map = Json.decodeFromString<Map<String, String>>(json)
            map[widgetId.toString()]
        }
    }

    suspend fun getAllData(): BackupData {
        val preferences = context.dataStore.data.first()
        val eventsJson = preferences[EVENTS_KEY] ?: "[]"
        val events = try { Json.decodeFromString<List<CountdownEvent>>(eventsJson) } catch(e: Exception) { emptyList() }
        val mode = preferences[THEME_MODE_KEY] ?: 0
        val color: String? = preferences[THEME_COLOR_KEY]
        val notify = preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
        return BackupData(events, mode, color, notify)
    }

    suspend fun restoreAllData(backup: BackupData) {
        context.dataStore.edit { preferences ->
            preferences[EVENTS_KEY] = Json.encodeToString(backup.events)
            preferences[THEME_MODE_KEY] = backup.themeMode
            if (backup.themeColor == null) preferences.remove(THEME_COLOR_KEY)
            else preferences[THEME_COLOR_KEY] = backup.themeColor
            preferences[NOTIFICATIONS_ENABLED_KEY] = backup.notificationsEnabled
        }
    }

    suspend fun getAllDataAsJson(): String {
        return Json.encodeToString(getAllData())
    }

    suspend fun restoreAllDataFromJson(json: String) {
        try {
            val backup = Json.decodeFromString<BackupData>(json)
            restoreAllData(backup)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
