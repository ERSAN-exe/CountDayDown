package com.Zero23.countdown.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

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
    val createdAt: Long = System.currentTimeMillis(),
    val customFontPath: String? = null,
    val excludedDays: List<Int>? = null // 1: Monday, ..., 7: Sunday
) {
    fun calculateTarget(now: LocalDateTime): LocalDateTime {
        var target = LocalDateTime.parse(targetDateTime)

        // Helper to get the next date based on repeat settings
        fun getNextOccurrence(current: LocalDateTime): LocalDateTime {
            return when (repeatType) {
                "daily" -> current.plusDays(1)
                "weekly" -> current.plusWeeks(1)
                "monthly" -> current.plusMonths(1)
                "yearly" -> current.plusYears(1)
                "custom" -> {
                    val interval = repeatInterval?.toLong() ?: 1L
                    when (repeatUnit) {
                        "seconds" -> current.plusSeconds(interval)
                        "minutes" -> current.plusMinutes(interval)
                        "hours" -> current.plusHours(interval)
                        "days" -> current.plusDays(interval)
                        "weeks" -> current.plusWeeks(interval)
                        "months" -> current.plusMonths(interval)
                        "years" -> current.plusYears(interval)
                        else -> current.plusDays(interval)
                    }
                }
                else -> current
            }
        }

        if (repeatType != null && repeatType != "none") {
            // Keep jumping by repeat interval until the date is in the future
            while (target.isBefore(now)) {
                val next = getNextOccurrence(target)
                if (next == target) break 
                target = next
            }
        }
        // Excluded days logic removed from target calculation to keep target date consistent
        return target
    }

    fun countExcludedDaysBetween(start: LocalDateTime, end: LocalDateTime): Long {
        if (excludedDays.isNullOrEmpty() || excludedDays.size >= 7) return 0
        
        var count = 0L
        
        // Use normalized start and end dates (ignoring time for pure day exclusion)
        var current = if (start.isBefore(end)) start.toLocalDate() else end.toLocalDate()
        val last = if (start.isBefore(end)) end.toLocalDate() else start.toLocalDate()
        
        // Rule: We only exclude days that have not yet passed.
        // If 'current' is today (start), we only exclude it if the event is in the future.
        // The while loop (current < last) correctly excludes all full days in between.
        // It does NOT exclude the target day (last), ensuring the countdown works on that day.
        
        // However, if we start counting FROM tomorrow (current.plusDays(1)), 
        // we avoid double-counting "today" which is already being handled by the partial time duration.
        if (current.isBefore(last)) {
            current = current.plusDays(1)
        }

        while (current.isBefore(last)) {
            if (excludedDays.contains(current.dayOfWeek.value)) {
                count++
            }
            current = current.plusDays(1)
        }
        return count
    }
}

@Serializable
data class SavedFont(
    val path: String,
    val name: String
)

@Serializable
data class BackupData(
    val events: List<CountdownEvent>,
    val themeMode: Int,
    val themeColor: String?,
    val notificationsEnabled: Boolean,
    val appBackgroundImage: String? = null,
    val appBackgroundBrightness: Float = 0.5f,
    val appBackgroundThemeColor: String? = null,
    val savedFonts: List<SavedFont> = emptyList()
)

class DataManager(private val context: Context) {
    private val eventsKey = stringPreferencesKey("events_list")
    private val themeModeKey = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
    private val themeColorKey = stringPreferencesKey("theme_color") // Hex color or null for default
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val widgetConfigKey = stringPreferencesKey("widget_config") // JSON map: widgetId (String) -> eventId (String)
    private val savedColorsKey = stringPreferencesKey("saved_colors") // JSON list of hex strings
    private val savedFontsKey = stringPreferencesKey("saved_fonts") // JSON list of SavedFont objects
    private val appBackgroundImageKey = stringPreferencesKey("app_bg_image")
    private val appBackgroundBrightnessKey = androidx.datastore.preferences.core.floatPreferencesKey("app_bg_brightness")
    private val appBackgroundThemeColorKey = stringPreferencesKey("app_bg_theme_color")

    val events: Flow<List<CountdownEvent>> = context.dataStore.data
        .map { it[eventsKey] ?: "[]" }
        .distinctUntilChanged()
        .map { json ->
            try { Json.decodeFromString<List<CountdownEvent>>(json) } catch (_: Exception) { emptyList() }
        }

    val savedColors: Flow<List<String>> = context.dataStore.data
        .map { it[savedColorsKey] ?: "[]" }
        .distinctUntilChanged()
        .map { json ->
            try { Json.decodeFromString<List<String>>(json) } catch (_: Exception) { emptyList() }
        }

    val savedFonts: Flow<List<SavedFont>> = context.dataStore.data
        .map { it[savedFontsKey] ?: "[]" }
        .distinctUntilChanged()
        .map { json ->
            try { Json.decodeFromString<List<SavedFont>>(json) } catch (_: Exception) { emptyList() }
        }

    suspend fun addSavedFont(font: SavedFont) {
        context.dataStore.edit { preferences ->
            val json = preferences[savedFontsKey] ?: "[]"
            val list = try { Json.decodeFromString<List<SavedFont>>(json).toMutableList() } catch(_: Exception) { mutableListOf() }
            if (list.none { it.path == font.path }) {
                list.add(0, font)
                preferences[savedFontsKey] = Json.encodeToString(list)
            }
        }
    }

    suspend fun removeSavedFont(font: SavedFont) {
        context.dataStore.edit { preferences ->
            val json = preferences[savedFontsKey] ?: "[]"
            val list = try { Json.decodeFromString<List<SavedFont>>(json).toMutableList() } catch(_: Exception) { mutableListOf() }
            if (list.remove(font)) {
                preferences[savedFontsKey] = Json.encodeToString(list)
                // Also delete the actual files
                try {
                    val file = java.io.File(font.path)
                    if (file.exists()) file.delete()
                    val nameFile = java.io.File("${font.path}.name")
                    if (nameFile.exists()) nameFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun addSavedColor(colorHex: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[savedColorsKey] ?: "[]"
            val list = try { Json.decodeFromString<List<String>>(json).toMutableList() } catch(_: Exception) { mutableListOf() }
            if (!list.contains(colorHex)) {
                list.add(0, colorHex)
                if (list.size > 20) list.removeAt(list.size - 1)
                preferences[savedColorsKey] = Json.encodeToString(list)
            }
        }
    }

    suspend fun removeSavedColor(colorHex: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[savedColorsKey] ?: "[]"
            val list = try { Json.decodeFromString<List<String>>(json).toMutableList() } catch(_: Exception) { mutableListOf() }
            if (list.remove(colorHex)) {
                preferences[savedColorsKey] = Json.encodeToString(list)
            }
        }
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[themeModeKey] ?: 0
    }

    val themeColor: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[themeColorKey]
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[notificationsEnabledKey] ?: true
    }

    val appBackgroundImage: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[appBackgroundImageKey]
    }

    val appBackgroundBrightness: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[appBackgroundBrightnessKey] ?: 0.5f
    }

    val appBackgroundThemeColor: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[appBackgroundThemeColorKey]
    }

    private val sortAscendingKey = booleanPreferencesKey("sort_ascending")
    private val sortByCreationKey = booleanPreferencesKey("sort_by_creation")
    private val isGridViewKey = booleanPreferencesKey("is_grid_view")

    val sortAscending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[sortAscendingKey] ?: false
    }

    val sortByCreationDate: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[sortByCreationKey] ?: true
    }

    val isGridView: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isGridViewKey] ?: false
    }

    suspend fun setSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[sortAscendingKey] = ascending
        }
    }

    suspend fun setSortByCreationDate(byCreation: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[sortByCreationKey] = byCreation
        }
    }

    suspend fun setIsGridView(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isGridViewKey] = isGrid
        }
    }

    suspend fun saveEvents(events: List<CountdownEvent>) {
        context.dataStore.edit { preferences ->
            preferences[eventsKey] = Json.encodeToString(events)
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = mode
        }
    }

    suspend fun setThemeColor(colorHex: String?) {
        context.dataStore.edit { preferences ->
            if (colorHex == null) preferences.remove(themeColorKey)
            else preferences[themeColorKey] = colorHex
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[notificationsEnabledKey] = enabled
        }
    }

    suspend fun setAppBackgroundImage(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) preferences.remove(appBackgroundImageKey)
            else preferences[appBackgroundImageKey] = uri
        }
    }

    suspend fun setAppBackgroundBrightness(brightness: Float) {
        context.dataStore.edit { preferences ->
            preferences[appBackgroundBrightnessKey] = brightness
        }
    }

    suspend fun setAppBackgroundThemeColor(colorHex: String?) {
        context.dataStore.edit { preferences ->
            if (colorHex == null) preferences.remove(appBackgroundThemeColorKey)
            else preferences[appBackgroundThemeColorKey] = colorHex
        }
    }

    suspend fun setWidgetEvent(widgetId: Int, eventId: String?) {
        context.dataStore.edit { preferences ->
            val json = preferences[widgetConfigKey] ?: "{}"
            val map = Json.decodeFromString<MutableMap<String, String>>(json)
            if (eventId == null) map.remove(widgetId.toString())
            else map[widgetId.toString()] = eventId
            preferences[widgetConfigKey] = Json.encodeToString(map)
        }
    }

    fun getWidgetEvent(widgetId: Int): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[widgetConfigKey] ?: "{}"
            val map = Json.decodeFromString<Map<String, String>>(json)
            map[widgetId.toString()]
        }
    }

    suspend fun getAllData(): BackupData {
        val preferences = context.dataStore.data.first()
        val eventsJson = preferences[eventsKey] ?: "[]"
        val events = try { Json.decodeFromString<List<CountdownEvent>>(eventsJson) } catch(_: Exception) { emptyList() }
        val mode = preferences[themeModeKey] ?: 0
        val color: String? = preferences[themeColorKey]
        val notify = preferences[notificationsEnabledKey] ?: true
        val bgImage = preferences[appBackgroundImageKey]
        val bgBrightness = preferences[appBackgroundBrightnessKey] ?: 0.5f
        val bgThemeColor = preferences[appBackgroundThemeColorKey]
        return BackupData(events, mode, color, notify, bgImage, bgBrightness, bgThemeColor)
    }

    suspend fun restoreAllData(backup: BackupData) {
        context.dataStore.edit { preferences ->
            preferences[eventsKey] = Json.encodeToString(backup.events)
            preferences[themeModeKey] = backup.themeMode
            if (backup.themeColor == null) preferences.remove(themeColorKey)
            else preferences[themeColorKey] = backup.themeColor
            preferences[notificationsEnabledKey] = backup.notificationsEnabled
            
            if (backup.appBackgroundImage == null) preferences.remove(appBackgroundImageKey)
            else preferences[appBackgroundImageKey] = backup.appBackgroundImage
            
            preferences[appBackgroundBrightnessKey] = backup.appBackgroundBrightness
            
            if (backup.appBackgroundThemeColor == null) preferences.remove(appBackgroundThemeColorKey)
            else preferences[appBackgroundThemeColorKey] = backup.appBackgroundThemeColor
        }
    }

    suspend fun restoreAllDataFromJson(json: String) {
        try {
            val backup = Json.decodeFromString<BackupData>(json)
            restoreAllData(backup)
        } catch (_: Exception) {}
    }
}
