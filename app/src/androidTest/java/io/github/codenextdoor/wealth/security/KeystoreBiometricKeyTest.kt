package io.github.codenextdoor.wealth.security

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The fingerprint-bound key in the real Keystore (this test's own alias, deleted after). */
@RunWith(AndroidJUnit4::class)
class KeystoreBiometricKeyTest {

    private val alias = "wealth_biometric_unlock_test"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @After
    fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @Test
    fun aSuccessCallbackWithoutTheRealPromptDoesNotUnlock() {
        val lock = AppLock(SettingsStore(context, "lock_biometric_${System.nanoTime()}"), biometricKey = KeystoreBiometricKey(alias), lifecycle = owner.lifecycle)
        lock.setPin("4827")
        lock.setBiometricEnabled(true)
        lock.leaveFor()

        val cipher = lock.biometricCipher()
        if (Biometrics.isAvailable(context)) {
            // A key exists, but only the system prompt can unlock it.
            assertNotNull(cipher)
            assertFalse(lock.unlockWithBiometric(cipher))
        } else {
            // No strong biometrics on this phone: no key can be made, and nothing crashes.
            assertNull(cipher)
            assertFalse(lock.unlockWithBiometric(null))
        }
        assertTrue(lock.isLocked.value)
        assertTrue(lock.unlockWithPin("4827"))
    }

    private fun AppLock.leaveFor() {
        setDelay(LockDelay.IMMEDIATELY)
        onAppBackgrounded()
        onAppForegrounded()
    }
}
