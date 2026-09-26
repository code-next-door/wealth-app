package io.github.codenextdoor.wealth.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores a PIN only as a salted, deliberately slow hash (PBKDF2-HMAC-SHA256),
 * so the PIN can't be read back even from a copy of the app's files.
 */
object PinHasher {

    data class Hashed(val hash: ByteArray, val salt: ByteArray, val iterations: Int)

    /** High enough to slow down guessing, low enough to unlock without a visible delay. */
    const val DEFAULT_ITERATIONS = 120_000

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Hashed(derive(pin, salt, iterations), salt, iterations)
    }

    fun verify(pin: String, stored: Hashed): Boolean =
        // Constant-time comparison, so timing doesn't reveal how close a guess was.
        MessageDigest.isEqual(derive(pin, stored.salt, stored.iterations), stored.hash)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Waiting time after [failedAttempts] wrong PINs in a row: none for the first 4, then growing. */
    fun lockoutSeconds(failedAttempts: Int): Long = when {
        failedAttempts < 5 -> 0
        else -> minOf(30L shl minOf(failedAttempts - 5, 10), 30L * 60) // 30 s, 1 min, 2 min, ... up to 30 min
    }
}
