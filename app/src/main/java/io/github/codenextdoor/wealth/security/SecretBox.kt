package io.github.codenextdoor.wealth.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Keeps a small secret (e.g. the automatic backup's password) stored only in sealed form. */
interface SecretBox {
    fun seal(secret: ByteArray): String

    /** Throws if the phone's key for it is gone. */
    fun open(sealed: String): ByteArray
}

/**
 * Seals with an AES key in the Android Keystore (secure hardware where the phone has
 * it), like the database key: usable by the app without a fingerprint, never readable.
 */
class KeystoreSecretBox(private val alias: String) : SecretBox {

    // The Keystore reads and writes files: on a background thread, the caller waits (a few ms),
    // like KeystorePinPepper, so the main thread never touches the disk (StrictMode).
    override fun seal(secret: ByteArray): String = runBlocking(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(secret), Base64.NO_WRAP)
    }

    override fun open(sealed: String): ByteArray = runBlocking(Dispatchers.IO) {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
        cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH)
    }

    private fun key(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "The key for this secret is gone" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
