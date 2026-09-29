package io.github.codenextdoor.wealth.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores a PIN only as a salted, deliberately slow hash (PBKDF2-HMAC-SHA256),
 * so the PIN can't be read back even from a copy of the app's files.
 *
 * Scheme 2 (since 0.4) also runs the hash through [PinPepper], a key that stays in
 * the phone's secure hardware: without the phone, a copied hash can't be tested
 * against the 10^4–10^8 possible PINs. Scheme 1 (older installs) is still read, and
 * `AppLock` re-stores it as scheme 2 at the next correct PIN.
 */
object PinHasher {

    data class Hashed(val hash: ByteArray, val salt: ByteArray, val iterations: Int, val scheme: Int = SCHEME_PEPPERED)

    /** PBKDF2 only (before 0.4). */
    const val SCHEME_PLAIN = 1

    /** PBKDF2, then HMAC with the phone's key. */
    const val SCHEME_PEPPERED = 2

    /** High enough to slow down guessing, low enough to unlock without a visible delay. */
    const val DEFAULT_ITERATIONS = 120_000

    /** A new PIN, always scheme 2 (the phone's key is made if it doesn't exist yet). */
    fun hash(pin: String, pepper: PinPepper, iterations: Int = DEFAULT_ITERATIONS): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Hashed(pepper.mac(derive(pin, salt, iterations), createKey = true), salt, iterations, SCHEME_PEPPERED)
    }

    /** Throws [PinPepper.Unavailable] if a scheme-2 PIN can't be checked on this phone. */
    fun verify(pin: String, stored: Hashed, pepper: PinPepper): Boolean {
        val derived = derive(pin, stored.salt, stored.iterations)
        val candidate = if (stored.scheme == SCHEME_PLAIN) derived else pepper.mac(derived)
        // Constant-time comparison, so timing doesn't reveal how close a guess was.
        return MessageDigest.isEqual(candidate, stored.hash)
    }

    /** The old way (scheme 1): only if the phone can't make its key, and in tests of the upgrade. */
    fun hashPlain(pin: String, iterations: Int = DEFAULT_ITERATIONS): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Hashed(derive(pin, salt, iterations), salt, iterations, SCHEME_PLAIN)
    }

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
