package io.github.codenextdoor.wealth.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * A key that only works right after the system's fingerprint/face prompt, so a faked
 * "success" (e.g. by a hooking tool) can't unlock the app: the prompt must really have
 * unlocked [cipher] for [confirm] to pass. It protects no data, so replacing it loses nothing.
 */
interface BiometricKey {
    /** A cipher for the prompt; null when fingerprints changed since the key was made (PIN first). */
    fun cipher(): Cipher?

    /** After a right PIN: replaces a key that fingerprint changes made unusable. */
    fun renew()

    /** True only if the prompt unlocked [cipher]. */
    fun confirm(cipher: Cipher): Boolean
}

class KeystoreBiometricKey(private val alias: String = "wealth_biometric_unlock") : BiometricKey {

    // The Keystore reads and writes files: on a background thread, the caller waits (a few ms),
    // like KeystorePinPepper, so the main thread never touches the disk (StrictMode).
    override fun cipher(): Cipher? = runBlocking(Dispatchers.IO) {
        try {
            // Made here the first time (also for users who turned biometrics on before this key
            // existed). An unusable key stays in place, so only a PIN can replace it.
            initCipher(existingKey() ?: newKey())
        } catch (e: KeyPermanentlyInvalidatedException) {
            null
        }
    }

    override fun renew() = runBlocking(Dispatchers.IO) {
        val key = existingKey() ?: return@runBlocking
        try {
            initCipher(key)
        } catch (e: KeyPermanentlyInvalidatedException) {
            keyStore().deleteEntry(alias)
            newKey()
        }
        Unit
    }

    override fun confirm(cipher: Cipher): Boolean = runBlocking(Dispatchers.IO) {
        try {
            cipher.doFinal(CHALLENGE).isNotEmpty()
        } catch (e: Exception) {
            // Not unlocked by the prompt (UserNotAuthenticated / IllegalBlockSize) or already used.
            false
        }
    }

    private fun initCipher(key: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Every use needs a fresh fingerprint/face check (0 s), strong biometrics only.
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                // Someone who adds their own fingerprint can't then use it to open the app.
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val CHALLENGE = "wealth-unlock".toByteArray()
    }
}
