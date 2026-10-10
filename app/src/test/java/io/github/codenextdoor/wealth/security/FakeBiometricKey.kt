package io.github.codenextdoor.wealth.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/**
 * A software stand-in for the fingerprint-bound Keystore key. [unlockedByPrompt] plays
 * the system prompt: only a cipher it approved works; [invalidated] = fingerprints changed.
 */
class FakeBiometricKey : BiometricKey {
    var invalidated = false
    var renewals = 0
    private val unlocked = mutableSetOf<Cipher>()

    override fun cipher(): Cipher? = if (invalidated) null else Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    }

    override fun renew() {
        invalidated = false
        renewals++
    }

    override fun confirm(cipher: Cipher): Boolean = cipher in unlocked

    fun unlockedByPrompt(cipher: Cipher): Cipher = cipher.also { unlocked += it }
}
