package io.github.codenextdoor.wealth.security

import android.content.Context
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long the app may be in the background before it locks again. */
enum class LockDelay(val millis: Long) { IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000) }

/**
 * App lock: PIN (stored as a slow hash) with optional biometric unlock.
 *
 * Settings live in plain SharedPreferences, like the appearance settings:
 * they're needed before the database opens, and contain nothing secret
 * (only the PIN's hash). The lock re-engages when the app has been in the
 * background for longer than the chosen [LockDelay].
 */
class AppLock(
    context: Context,
    prefsName: String = "app_lock",
    /** The whole app's foreground/background lifecycle; replaceable in tests. */
    lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LockSettings> = _settings.asStateFlow()

    private val _isLocked = MutableStateFlow(_settings.value.enabled)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAt: Long? = null
    /** When a system picker was opened; leaving right after that doesn't lock. */
    private var briefExitAllowedAt: Long? = null

    data class LockSettings(
        val enabled: Boolean,
        val biometricEnabled: Boolean,
        val delay: LockDelay,
        val hideInRecents: Boolean,
    )

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = onAppBackgrounded()
            override fun onStart(owner: LifecycleOwner) = onAppForegrounded()
        })
    }

    /**
     * Call right before opening a system screen for the user (e.g. the file
     * picker), so coming back from it doesn't ask for the PIN.
     */
    fun allowBriefExit() {
        briefExitAllowedAt = clock()
    }

    internal fun onAppBackgrounded() {
        backgroundedAt = clock()
    }

    internal fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // Only a picker opened just before leaving counts; otherwise the exception
        // would linger and skip the lock the next time the user really leaves.
        val allowedAt = briefExitAllowedAt
        briefExitAllowedAt = null
        if (allowedAt != null && since - allowedAt in 0..BRIEF_EXIT_WINDOW_MS) return
        val s = _settings.value
        if (s.enabled && clock() - since >= s.delay.millis) _isLocked.value = true
    }

    // ---- Unlocking ---------------------------------------------------------

    /** Seconds until another PIN attempt is allowed; 0 when allowed now. */
    fun secondsUntilNextAttempt(): Long {
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        // Round up, so "0" really means an attempt is allowed now.
        return ((until - clock() + 999) / 1000).coerceAtLeast(0)
    }

    /** Checks [pin]; unlocks on success. Returns false for a wrong PIN or while locked out. */
    fun unlockWithPin(pin: String): Boolean {
        if (secondsUntilNextAttempt() > 0) return false
        val ok = storedPin()?.let { PinHasher.verify(pin, it) } == true
        if (ok) {
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply()
            _isLocked.value = false
        } else {
            val failed = prefs.getInt(KEY_FAILED, 0) + 1
            val wait = PinHasher.lockoutSeconds(failed)
            prefs.edit().putInt(KEY_FAILED, failed).putLong(KEY_LOCKOUT_UNTIL, clock() + wait * 1000).apply()
        }
        return ok
    }

    /** After a successful biometric check (verified by the system prompt). */
    fun unlockWithBiometric() {
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply()
        _isLocked.value = false
    }

    // ---- Settings ----------------------------------------------------------

    fun verifyPin(pin: String): Boolean = storedPin()?.let { PinHasher.verify(pin, it) } == true

    /** Turns the lock on (or changes the PIN). */
    fun setPin(pin: String) {
        val hashed = PinHasher.hash(pin)
        prefs.edit()
            .putString(KEY_HASH, Base64.encodeToString(hashed.hash, Base64.NO_WRAP))
            .putString(KEY_SALT, Base64.encodeToString(hashed.salt, Base64.NO_WRAP))
            .putInt(KEY_ITERATIONS, hashed.iterations)
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_FAILED, 0)
            .apply()
        refresh()
    }

    fun disable() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_ITERATIONS)
            .putBoolean(KEY_ENABLED, false).putBoolean(KEY_BIOMETRIC, false).apply()
        _isLocked.value = false
        refresh()
    }

    fun setBiometricEnabled(enabled: Boolean) = update { putBoolean(KEY_BIOMETRIC, enabled) }
    fun setDelay(delay: LockDelay) = update { putString(KEY_DELAY, delay.name) }
    fun setHideInRecents(hide: Boolean) = update { putBoolean(KEY_HIDE_RECENTS, hide) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        refresh()
    }

    private fun refresh() {
        _settings.value = readSettings()
    }

    private fun readSettings() = LockSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false) && prefs.contains(KEY_HASH),
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
        delay = prefs.getString(KEY_DELAY, null)?.let { runCatching { LockDelay.valueOf(it) }.getOrNull() } ?: LockDelay.ONE_MINUTE,
        hideInRecents = prefs.getBoolean(KEY_HIDE_RECENTS, true),
    )

    private fun storedPin(): PinHasher.Hashed? {
        val hash = prefs.getString(KEY_HASH, null) ?: return null
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        return PinHasher.Hashed(
            Base64.decode(hash, Base64.NO_WRAP),
            Base64.decode(salt, Base64.NO_WRAP),
            prefs.getInt(KEY_ITERATIONS, PinHasher.DEFAULT_ITERATIONS),
        )
    }

    private companion object {
        /** How soon after opening a picker the app must go to the background to count as "brief". */
        const val BRIEF_EXIT_WINDOW_MS = 5_000L
        const val KEY_ENABLED = "enabled"
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val KEY_ITERATIONS = "pin_iterations"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_DELAY = "delay"
        const val KEY_HIDE_RECENTS = "hide_in_recents"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
