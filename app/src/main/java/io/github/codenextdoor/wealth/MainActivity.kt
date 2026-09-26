package io.github.codenextdoor.wealth

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.codenextdoor.wealth.data.preferences.ThemeMode
import io.github.codenextdoor.wealth.ui.WealthApp
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearance = (application as WealthApplication).container.appearancePreferences
        setContent {
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val useWallpaperColors by appearance.useWallpaperColors.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not the system's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            WealthTheme(darkTheme = darkTheme, dynamicColor = useWallpaperColors) {
                WealthApp()
            }
        }
    }

    private companion object {
        // Defaults used by enableEdgeToEdge for button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
