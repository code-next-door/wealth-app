package io.github.codenextdoor.wealth.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val pepper = FakePinPepper()

    @Test
    fun verifiesOnlyTheRightPin() {
        val stored = PinHasher.hash("4827", pepper, iterations = 1_000)
        assertEquals(PinHasher.SCHEME_PEPPERED, stored.scheme)
        assertTrue(PinHasher.verify("4827", stored, pepper))
        assertFalse(PinHasher.verify("4828", stored, pepper))
        assertFalse(PinHasher.verify("", stored, pepper))
    }

    @Test
    fun theHashIsUselessWithoutThePhonesKey() {
        val stored = PinHasher.hash("4827", pepper, iterations = 1_000)
        // Someone with a copy of the files but another key (or none) can't test PINs.
        assertFalse(PinHasher.verify("4827", stored, FakePinPepper("another phone")))
        assertThrows(PinPepper.Unavailable::class.java) { PinHasher.verify("4827", stored, FakePinPepper().apply { available = false }) }
    }

    @Test
    fun oldPlainHashesStillVerify() {
        val old = PinHasher.hashPlain("4827", iterations = 1_000)
        assertEquals(PinHasher.SCHEME_PLAIN, old.scheme)
        assertTrue(PinHasher.verify("4827", old, FakePinPepper().apply { available = false })) // no key needed
        assertFalse(PinHasher.verify("4828", old, pepper))
    }

    @Test
    fun samePinGetsDifferentSaltAndHash() {
        val a = PinHasher.hash("1234", pepper, iterations = 1_000)
        val b = PinHasher.hash("1234", pepper, iterations = 1_000)
        assertFalse(a.salt.contentEquals(b.salt))
        assertNotEquals(a.hash.toList(), b.hash.toList())
    }

    @Test
    fun lockoutGrowsAfterFiveAttempts() {
        assertEquals(0, PinHasher.lockoutSeconds(4))
        assertEquals(30, PinHasher.lockoutSeconds(5))
        assertEquals(60, PinHasher.lockoutSeconds(6))
        assertEquals(30 * 60, PinHasher.lockoutSeconds(20))
    }
}
