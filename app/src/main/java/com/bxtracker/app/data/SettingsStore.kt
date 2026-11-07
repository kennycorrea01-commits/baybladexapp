package com.bxtracker.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.bxtracker.app.R
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(@StringRes val label: Int, @StringRes val description: Int) {
    SYSTEM(R.string.theme_system, R.string.theme_system_desc),
    LIGHT(R.string.theme_light, R.string.theme_light_desc),
    OLED(R.string.theme_oled, R.string.theme_oled_desc),
}

enum class PhotoSize(@StringRes val label: Int, @StringRes val description: Int, val maxSide: Int) {
    SMALL(R.string.photo_small, R.string.photo_small_desc, 720),
    STANDARD(R.string.photo_standard, R.string.photo_standard_desc, 1080),
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.OLED,
    val pointsToWin: Int = 4,
    val finishPoints: Map<Finish, Int> = Finish.entries.associateWith { it.points },
    /** Start of the current season (millis), or null to count everything. */
    val seasonStart: Long? = null,
    val photoSize: PhotoSize = PhotoSize.STANDARD,
) {
    fun pointsFor(finish: Finish): Int = finishPoints[finish] ?: finish.points
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val pointsKey = intPreferencesKey("points_to_win")
    private val seasonKey = longPreferencesKey("season_start")
    private val photoKey = stringPreferencesKey("photo_size")
    private fun finishKey(f: Finish) = intPreferencesKey("finish_${f.name.lowercase()}")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.OLED,
            pointsToWin = prefs[pointsKey] ?: 4,
            finishPoints = Finish.entries.associateWith { prefs[finishKey(it)] ?: it.points },
            seasonStart = prefs[seasonKey],
            photoSize = prefs[photoKey]?.let { runCatching { PhotoSize.valueOf(it) }.getOrNull() } ?: PhotoSize.STANDARD,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setPointsToWin(points: Int) {
        context.dataStore.edit { it[pointsKey] = points }
    }

    suspend fun setFinishPoints(finish: Finish, points: Int) {
        context.dataStore.edit { it[finishKey(finish)] = points }
    }

    suspend fun setSeasonStart(millis: Long?) {
        context.dataStore.edit { if (millis == null) it.remove(seasonKey) else it[seasonKey] = millis }
    }

    suspend fun setPhotoSize(size: PhotoSize) {
        context.dataStore.edit { it[photoKey] = size.name }
    }
}
