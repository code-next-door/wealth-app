package io.github.codenextdoor.wealth.security

import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long the app may be in the background before it locks again. */
enum class LockDelay(val millis: Long) { IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000) }

/**
 * App lock: PIN (stored as a slow hash) with optional biometric unlock.
 *
 * Settings live in their own settings file, like the appearance settings:
 * they're needed before the database opens, and contain nothing secret
 * (only the PIN's hash). The lock re-engages when the app has been in the
 * background for longer than the chosen [LockDelay].
 */
class AppLock(
    private val store: SettingsStore,
    /** The whole app's foreground/background lifecycle; replaceable in tests. */
    lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val clock: () -> Long = System::currentTimeMillis,
) {

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
        val until = store.current[KEY_LOCKOUT_UNTIL] ?: 0
        // Round up, so "0" really means an attempt is allowed now.
        return ((until - clock() + 999) / 1000).coerceAtLeast(0)
    }

    /** Checks [pin]; unlocks on success. Returns false for a wrong PIN or while locked out. */
    fun unlockWithPin(pin: String): Boolean {
        if (secondsUntilNextAttempt() > 0) return false
        val ok = storedPin()?.let { PinHasher.verify(pin, it) } == true
        if (ok) {
            resetAttempts()
            _isLocked.value = false
        } else {
            val failed = (store.current[KEY_FAILED] ?: 0) + 1
            val wait = PinHasher.lockoutSeconds(failed)
            store.edit {
                it[KEY_FAILED] = failed
                it[KEY_LOCKOUT_UNTIL] = clock() + wait * 1000
            }
        }
        return ok
    }

    /** After a successful biometric check (verified by the system prompt). */
    fun unlockWithBiometric() {
        resetAttempts()
        _isLocked.value = false
    }

    private fun resetAttempts() = store.edit {
        it[KEY_FAILED] = 0
        it[KEY_LOCKOUT_UNTIL] = 0
    }

    // ---- Settings ----------------------------------------------------------

    fun verifyPin(pin: String): Boolean = storedPin()?.let { PinHasher.verify(pin, it) } == true

    /** Turns the lock on (or changes the PIN). */
    fun setPin(pin: String) {
        val hashed = PinHasher.hash(pin)
        store.edit {
            it[KEY_HASH] = Base64.encodeToString(hashed.hash, Base64.NO_WRAP)
            it[KEY_SALT] = Base64.encodeToString(hashed.salt, Base64.NO_WRAP)
            it[KEY_ITERATIONS] = hashed.iterations
            it[KEY_ENABLED] = true
            it[KEY_FAILED] = 0
        }
        refresh()
    }

    fun disable() {
        store.edit {
            it.remove(KEY_HASH)
            it.remove(KEY_SALT)
            it.remove(KEY_ITERATIONS)
            it[KEY_ENABLED] = false
            it[KEY_BIOMETRIC] = false
        }
        _isLocked.value = false
        refresh()
    }

    fun setBiometricEnabled(enabled: Boolean) = update { it[KEY_BIOMETRIC] = enabled }
    fun setDelay(delay: LockDelay) = update { it[KEY_DELAY] = delay.name }
    fun setHideInRecents(hide: Boolean) = update { it[KEY_HIDE_RECENTS] = hide }

    private fun update(block: (MutablePreferences) -> Unit) {
        store.edit(block)
        refresh()
    }

    private fun refresh() {
        _settings.value = readSettings()
    }

    private fun readSettings(): LockSettings {
        val prefs = store.current
        return LockSettings(
            enabled = (prefs[KEY_ENABLED] ?: false) && prefs[KEY_HASH] != null,
            biometricEnabled = prefs[KEY_BIOMETRIC] ?: false,
            delay = prefs[KEY_DELAY]?.let { runCatching { LockDelay.valueOf(it) }.getOrNull() } ?: LockDelay.ONE_MINUTE,
            hideInRecents = prefs[KEY_HIDE_RECENTS] ?: true,
        )
    }

    private fun storedPin(): PinHasher.Hashed? {
        val prefs = store.current
        val hash = prefs[KEY_HASH] ?: return null
        val salt = prefs[KEY_SALT] ?: return null
        return PinHasher.Hashed(
            Base64.decode(hash, Base64.NO_WRAP),
            Base64.decode(salt, Base64.NO_WRAP),
            prefs[KEY_ITERATIONS] ?: PinHasher.DEFAULT_ITERATIONS,
        )
    }

    private companion object {
        /** How soon after opening a picker the app must go to the background to count as "brief". */
        const val BRIEF_EXIT_WINDOW_MS = 5_000L

        // The names the older SharedPreferences file used, so its values carry over.
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_HASH = stringPreferencesKey("pin_hash")
        val KEY_SALT = stringPreferencesKey("pin_salt")
        val KEY_ITERATIONS = intPreferencesKey("pin_iterations")
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric")
        val KEY_DELAY = stringPreferencesKey("delay")
        val KEY_HIDE_RECENTS = booleanPreferencesKey("hide_in_recents")
        val KEY_FAILED = intPreferencesKey("failed_attempts")
        val KEY_LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
    }
}
