package io.github.codenextdoor.wealth.security

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
    private fun lock(name: String = "lock_${System.nanoTime()}") =
        AppLock(ApplicationProvider.getApplicationContext(), prefsName = name, lifecycle = owner.lifecycle, clock = { now })

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
        lock(name).setPin("4827")
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
}
