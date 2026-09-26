package io.github.codenextdoor.wealth.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the database passphrase.
 *
 * On first launch a random 256-bit passphrase is generated. It is stored
 * encrypted ("wrapped") with an AES key that lives in the Android Keystore,
 * which keeps that key in secure hardware where available and never lets
 * it leave. So the passphrase is never on disk in plain form, and copying the
 * app's files to another device does not make the database readable.
 */
class DatabaseKeyManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        val wrapped = prefs.getString(PREF_WRAPPED, null)
        val raw = if (wrapped != null) unwrap(wrapped) else createAndStore()
        // Hex text avoids zero bytes, which some SQLCipher code paths treat as
        // the end of the passphrase.
        return raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    private fun createAndStore(): ByteArray {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val stored = Base64.encodeToString(cipher.iv + cipher.doFinal(raw), Base64.NO_WRAP)
        // commit() (synchronous) so the key is on disk before the database is created with it.
        check(prefs.edit().putString(PREF_WRAPPED, stored).commit()) { "Could not store database key" }
        return raw
    }

    private fun unwrap(stored: String): ByteArray {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wealth_db_key_wrapper"
        const val PREFS_NAME = "database_key"
        const val PREF_WRAPPED = "wrapped_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
