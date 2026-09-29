package io.github.codenextdoor.wealth.security

import androidx.core.content.edit
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockTest {

    private var now = 1_000_000L
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private val pepper = FakePinPepper()

    private fun lock(name: String = "lock_${System.nanoTime()}", pepper: PinPepper = this.pepper) =
        AppLock(SettingsStore(ApplicationProvider.getApplicationContext(), name), pepper, lifecycle = owner.lifecycle, clock = { now })

    private fun AppLock.leaveFor(millis: Long) {
        onAppBackgrounded()
        now += millis
        onAppForegrounded()
    }

    @Test
    fun offByDefault() {
        val lock = lock()
        assertFalse(lock.settings.value.enabled)
        lock.leaveFor(10 * 60_000)
        assertFalse(lock.isLocked.value)
        assertTrue(lock.settings.value.hideInRecents) // privacy on by default
    }

    @Test
    fun locksAfterTheChosenDelay() {
        val lock = lock()
        lock.setPin("4827")
        lock.leaveFor(30_000)
        assertFalse(lock.isLocked.value) // default is 1 minute
        lock.leaveFor(61_000)
        assertTrue(lock.isLocked.value)
        assertTrue(lock.unlockWithPin("4827"))
        assertFalse(lock.isLocked.value)

        lock.setDelay(LockDelay.IMMEDIATELY)
        lock.leaveFor(10)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun lockedWhenTheAppStartsIfEnabled() {
        val name = "lock_restart"
        val store = SettingsStore(ApplicationProvider.getApplicationContext(), name)
        AppLock(store, pepper, lifecycle = owner.lifecycle, clock = { now }).setPin("4827")
        store.close()
        assertTrue(lock(name).isLocked.value) // a fresh process starts locked
    }

    @Test
    fun wrongPinsLeadToWaiting() {
        val lock = lock()
        lock.setPin("4827")
        lock.leaveFor(120_000)
        repeat(4) { assertFalse(lock.unlockWithPin("0000")) }
        assertEquals(0, lock.secondsUntilNextAttempt())
        assertFalse(lock.unlockWithPin("0000")) // 5th wrong
        assertEquals(30, lock.secondsUntilNextAttempt())
        assertFalse(lock.unlockWithPin("4827")) // even the right PIN waits
        now += 30_000
        assertTrue(lock.unlockWithPin("4827"))
        assertEquals(0, lock.secondsUntilNextAttempt())
    }

    @Test
    fun filePickerExceptionOnlyCoversTheNextExit() {
        val lock = lock()
        lock.setPin("4827")
        lock.setDelay(LockDelay.IMMEDIATELY)
        lock.allowBriefExit()
        lock.leaveFor(20_000)
        assertFalse(lock.isLocked.value) // returning from the picker

        lock.leaveFor(1_000)
        assertTrue(lock.isLocked.value) // the next real exit locks
    }

    @Test
    fun staleFilePickerExceptionIsIgnored() {
        val lock = lock()
        lock.setPin("4827")
        lock.setDelay(LockDelay.IMMEDIATELY)
        lock.allowBriefExit()
        now += 60_000 // picker was never opened; user leaves much later
        lock.leaveFor(1_000)
        assertTrue(lock.isLocked.value)
    }

    @Test
    fun changingAndDisabling() {
        val lock = lock()
        lock.setPin("1111")
        lock.setPin("2222")
        assertFalse(lock.verifyPin("1111"))
        assertTrue(lock.verifyPin("2222"))
        lock.setBiometricEnabled(true)
        lock.disable()
        assertFalse(lock.settings.value.enabled)
        assertFalse(lock.settings.value.biometricEnabled)
        assertFalse(lock.verifyPin("2222"))
    }

    @Test
    fun aPinSetBeforeTheMoveToDataStoreStillWorks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "lock_old_${System.nanoTime()}"
        val hashed = PinHasher.hashPlain("4827", iterations = 1_000) // as older versions stored it
        // Exactly as the SharedPreferences version stored it.
        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit(commit = true) {
            putString("pin_hash", android.util.Base64.encodeToString(hashed.hash, android.util.Base64.NO_WRAP))
            putString("pin_salt", android.util.Base64.encodeToString(hashed.salt, android.util.Base64.NO_WRAP))
            putInt("pin_iterations", hashed.iterations)
            putBoolean("enabled", true)
            putBoolean("biometric", true)
            putString("delay", LockDelay.FIVE_MINUTES.name)
            putBoolean("hide_in_recents", false)
            putInt("failed_attempts", 3)
            putLong("lockout_until", 0)
        }

        val lock = lock(name)
        assertEquals(AppLock.LockSettings(enabled = true, biometricEnabled = true, delay = LockDelay.FIVE_MINUTES, hideInRecents = false), lock.settings.value)
        assertTrue(lock.isLocked.value)
        assertFalse(lock.unlockWithPin("0000")) // the 4th wrong one: still no wait...
        assertEquals(0, lock.secondsUntilNextAttempt())
        assertFalse(lock.unlockWithPin("0000")) // ...the 5th is: the old count carried over
        assertEquals(30, lock.secondsUntilNextAttempt())
        now += 30_000
        assertTrue(lock.unlockWithPin("4827"))
        // The old file is emptied once the move is saved.
        assertTrue(context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun anOldPinIsAcceptedAndUpgradedToThePhoneKeyOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "lock_scheme1_${System.nanoTime()}"
        val old = PinHasher.hashPlain("4827", iterations = 1_000)
        val store = SettingsStore(context, name)
        store.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("pin_hash")] = android.util.Base64.encodeToString(old.hash, android.util.Base64.NO_WRAP)
            it[androidx.datastore.preferences.core.stringPreferencesKey("pin_salt")] = android.util.Base64.encodeToString(old.salt, android.util.Base64.NO_WRAP)
            it[androidx.datastore.preferences.core.intPreferencesKey("pin_iterations")] = old.iterations
            it[androidx.datastore.preferences.core.booleanPreferencesKey("enabled")] = true
        }
        val scheme = androidx.datastore.preferences.core.intPreferencesKey("pin_scheme")
        val lock = AppLock(store, pepper, lifecycle = owner.lifecycle, clock = { now })

        assertFalse(lock.unlockWithPin("0000"))
        assertEquals(null, store.current[scheme]) // a wrong PIN upgrades nothing
        assertTrue(lock.unlockWithPin("4827")) // same PIN as before the update
        assertEquals(PinHasher.SCHEME_PEPPERED, store.current[scheme])
        // Now tied to this phone's key: another key can't check it, this one can.
        assertFalse(AppLock(store, FakePinPepper("another phone"), lifecycle = owner.lifecycle, clock = { now }).verifyPin("4827"))
        assertTrue(lock.verifyPin("4827"))
    }

    @Test
    fun aLostPhoneKeyMeansThePinCantBeCheckedNotThatItsWrong() {
        val lock = lock()
        lock.setPin("4827")
        lock.leaveFor(120_000)
        pepper.available = false
        repeat(6) { assertFalse(lock.unlockWithPin("4827")) }
        assertTrue(lock.pinUnverifiable.value)
        assertEquals(0, lock.secondsUntilNextAttempt()) // not counted as wrong guesses
        assertTrue(lock.isLocked.value)
        lock.unlockForRecovery() // allowed only now (MainActivity also needs the data unreadable)
        assertFalse(lock.isLocked.value)
    }

    @Test(expected = IllegalStateException::class)
    fun recoveryCantSkipAWorkingLock() {
        val lock = lock()
        lock.setPin("4827")
        lock.unlockForRecovery()
    }
}
