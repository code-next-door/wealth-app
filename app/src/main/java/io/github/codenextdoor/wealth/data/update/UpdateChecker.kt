package io.github.codenextdoor.wealth.data.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Looks for a newer release at most once a day (on opening the app, and each new day),
 * unless switched off. Builds without a release version (debug, tests) never look.
 * A newer release waits in [available] until shown and answered.
 */
class UpdateChecker(
    private val source: UpdateSource,
    private val preferences: UpdatePreferences,
    /** This build's version, e.g. "0.7.0"; null for builds that aren't releases. */
    val currentVersion: String?,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available.asStateFlow()

    private val _enabled = MutableStateFlow(preferences.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun checkIfDue() {
        val current = currentVersion ?: return
        val day = today()
        if (!preferences.enabled || preferences.lastChecked == day) return
        // Offline or no answer: not counted as checked, so the next start tries again.
        val latest = source.latest() ?: return
        preferences.setLastChecked(day)
        if (AppVersion.isNewer(latest.version, current)) _available.value = latest
    }

    /** Settings › "Check now": looks even if it already did today. */
    suspend fun checkNow(): Release? {
        val current = currentVersion ?: return null
        val latest = source.latest() ?: return null
        preferences.setLastChecked(today())
        _available.value = latest.takeIf { AppVersion.isNewer(it.version, current) }
        return _available.value
    }

    /** "Later": asked again on another day. */
    fun dismiss() {
        _available.value = null
    }

    fun setEnabled(on: Boolean) {
        preferences.setEnabled(on)
        _enabled.value = on
        if (!on) _available.value = null
    }
}

/**
 * This build's version if it's a release (CI sets the version code from the Git tag;
 * local and test builds keep 1), else null.
 */
fun releaseVersion(context: Context): String? {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.versionName.takeIf { info.longVersionCode > 1 }
}
