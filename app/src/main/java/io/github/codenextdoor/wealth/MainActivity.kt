package io.github.codenextdoor.wealth

import androidx.compose.material3.Surface
import io.github.codenextdoor.wealth.security.RecoveryRoute
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.ui.tour.LocalTour
import androidx.compose.runtime.LaunchedEffect
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.codenextdoor.wealth.data.preferences.ThemeMode
import io.github.codenextdoor.wealth.security.Biometrics
import io.github.codenextdoor.wealth.security.LockScreen
import io.github.codenextdoor.wealth.ui.Figures
import io.github.codenextdoor.wealth.ui.LocalFigures
import io.github.codenextdoor.wealth.ui.WealthApp
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

// FragmentActivity (rather than ComponentActivity) because the system
// biometric prompt needs one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WealthApplication).container
        val appearance = container.appearancePreferences
        val appLock = container.appLock
        setContent {
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val useWallpaperColors by appearance.useWallpaperColors.collectAsStateWithLifecycle()
            val figuresHidden by appearance.figuresHidden.collectAsStateWithLifecycle()
            val databaseState by container.databaseState.collectAsStateWithLifecycle()
            val pinUnverifiable by appLock.pinUnverifiable.collectAsStateWithLifecycle()
            val lockSettings by appLock.settings.collectAsStateWithLifecycle()
            val isLocked by appLock.isLocked.collectAsStateWithLifecycle()
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
            // Keeps balances out of the recent-apps preview (Android 13+; Android 12 has no
            // way to do only that). Screenshots and recordings stay allowed: FLAG_SECURE would
            // block them too, which the author didn't want.
            DisposableEffect(lockSettings.hideInRecents) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!lockSettings.hideInRecents)
                }
                onDispose {}
            }
            val figures = remember(figuresHidden) { Figures(figuresHidden) { appearance.setFiguresHidden(!figuresHidden) } }
            WealthTheme(darkTheme = darkTheme, dynamicColor = useWallpaperColors) {
                // The tour ends if the app locks; it's shown over the home screen only.
                LaunchedEffect(isLocked) { if (isLocked) container.tour.stop() }
                CompositionLocalProvider(LocalFigures provides figures, LocalTour provides container.tour) {
                    Box(Modifier.fillMaxSize()) {
                        // The app stays in place under the lock (so you return to the same
                        // screen), but is invisible and hidden from screen readers meanwhile.
                        val hidden = if (isLocked) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier
                        Box(Modifier.fillMaxSize().then(hidden)) {
                            // The app only once its data is open; if the key is gone, the way out.
                            when (databaseState) {
                                DatabaseState.Ready -> WealthApp()
                                is DatabaseState.Unreadable -> RecoveryRoute()
                                DatabaseState.Opening -> Surface(Modifier.fillMaxSize()) {}
                            }
                        }
                        if (isLocked) {
                            val title = stringResource(R.string.lock_biometric_title)
                            val usePin = stringResource(R.string.lock_use_pin)
                            val biometricReady = lockSettings.biometricEnabled && Biometrics.isAvailable(this@MainActivity)
                            LockScreen(
                                biometricEnabled = biometricReady,
                                onPin = appLock::unlockWithPin,
                                secondsUntilNextAttempt = appLock::secondsUntilNextAttempt,
                                onBiometric = {
                                    Biometrics.prompt(this@MainActivity, title, usePin) { appLock.unlockWithBiometric() }
                                },
                                pinUnavailable = pinUnverifiable,
                                // Only a way to the recovery screen: the data behind it can't be read.
                                onOpenRecovery = if (pinUnverifiable && databaseState is DatabaseState.Unreadable) appLock::unlockForRecovery else null,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        // Defaults used by enableEdgeToEdge for button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
