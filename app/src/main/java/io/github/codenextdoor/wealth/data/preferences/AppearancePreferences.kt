package io.github.codenextdoor.wealth.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Look-and-feel choices. Kept in their own settings file rather than the
 * encrypted database: they aren't sensitive, and they must be available
 * instantly at startup so the first frame already uses the right theme.
 */
class AppearancePreferences(private val store: SettingsStore) {

    private val _themeMode = MutableStateFlow(
        store.current[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useWallpaperColors = MutableStateFlow(store.current[WALLPAPER] ?: false)
    val useWallpaperColors: StateFlow<Boolean> = _useWallpaperColors.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
        _themeMode.value = mode
    }

    fun setUseWallpaperColors(enabled: Boolean) {
        store.edit { it[WALLPAPER] = enabled }
        _useWallpaperColors.value = enabled
    }

    private companion object {
        // The names the older SharedPreferences file used, so its values carry over.
        val THEME = stringPreferencesKey("theme_mode")
        val WALLPAPER = booleanPreferencesKey("use_wallpaper_colors")
    }
}
