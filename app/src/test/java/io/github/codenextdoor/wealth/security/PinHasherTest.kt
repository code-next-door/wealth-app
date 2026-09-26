package io.github.codenextdoor.wealth.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun verifiesOnlyTheRightPin() {
        val stored = PinHasher.hash("4827", iterations = 1_000)
        assertTrue(PinHasher.verify("4827", stored))
        assertFalse(PinHasher.verify("4828", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun samePinGetsDifferentSaltAndHash() {
        val a = PinHasher.hash("1234", iterations = 1_000)
        val b = PinHasher.hash("1234", iterations = 1_000)
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
