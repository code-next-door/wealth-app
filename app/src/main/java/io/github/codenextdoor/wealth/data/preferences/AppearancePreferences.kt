package io.github.codenextdoor.wealth.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Look-and-feel choices. Kept in plain SharedPreferences rather than the
 * encrypted database: they aren't sensitive, and they must be available
 * instantly at startup so the first frame already uses the right theme.
 */
class AppearancePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useWallpaperColors = MutableStateFlow(prefs.getBoolean(KEY_WALLPAPER, false))
    val useWallpaperColors: StateFlow<Boolean> = _useWallpaperColors.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setUseWallpaperColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER, enabled).apply()
        _useWallpaperColors.value = enabled
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_WALLPAPER = "use_wallpaper_colors"
    }
}
