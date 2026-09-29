package io.github.codenextdoor.wealth.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The PIN's phone-held key in the real Keystore (this test's own alias, deleted after). */
@RunWith(AndroidJUnit4::class)
class KeystorePinPepperTest {

    private val alias = "wealth_pin_pepper_test"

    @After
    fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @Test
    fun sameInputSameResultAndNothingMadeWhenOnlyChecking() {
        val pepper = KeystorePinPepper(alias)
        // Checking before any PIN was set: never makes a key (that would break the real PIN).
        assertThrows(PinPepper.Unavailable::class.java) { pepper.mac(byteArrayOf(1, 2, 3)) }

        val first = pepper.mac(byteArrayOf(1, 2, 3), createKey = true)
        assertTrue(first.contentEquals(pepper.mac(byteArrayOf(1, 2, 3))))
        assertFalse(first.contentEquals(pepper.mac(byteArrayOf(1, 2, 4))))

        // A PIN set with this key, checked the way the lock does.
        val stored = PinHasher.hash("4827", pepper, iterations = 1_000)
        assertTrue(PinHasher.verify("4827", stored, pepper))
        assertFalse(PinHasher.verify("4828", stored, pepper))

        deleteKey() // as if the Keystore lost it
        assertThrows(PinPepper.Unavailable::class.java) { PinHasher.verify("4827", stored, pepper) }
    }
}
