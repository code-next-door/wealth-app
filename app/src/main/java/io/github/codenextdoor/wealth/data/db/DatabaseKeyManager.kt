package io.github.codenextdoor.wealth.data.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
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
class DatabaseKeyManager(
    /** The key's settings file (`AppContainer`: [STORE_NAME]). */
    private val store: SettingsStore,
    /** Whether a database file is already there, i.e. made with an earlier key. */
    private val databaseExists: () -> Boolean,
) {

    /** No stored key although a database exists: never replace it with a new one. */
    class MissingKey : IllegalStateException("The database key is missing; not creating a new one over the existing database")

    fun getOrCreatePassphrase(): ByteArray {
        val wrapped = store.current[PREF_WRAPPED]
        val raw = when {
            wrapped != null -> unwrap(wrapped)
            // Something lost the key: a new one can't open the existing database,
            // so stop rather than store one over whatever is left.
            databaseExists() -> throw MissingKey()
            else -> createAndStore()
        }
        // Hex text avoids zero bytes, which some SQLCipher code paths treat as
        // the end of the passphrase.
        return raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    private fun createAndStore(): ByteArray {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val stored = Base64.encodeToString(cipher.iv + cipher.doFinal(raw), Base64.NO_WRAP)
        // On disk before the database is created with it (edit returns once saved, or throws).
        store.edit { it[PREF_WRAPPED] = stored }
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

    companion object {
        /** Also the older SharedPreferences file's name, so the key moves over from it. */
        const val STORE_NAME = "database_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wealth_db_key_wrapper"
        private val PREF_WRAPPED = stringPreferencesKey("wrapped_passphrase")
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
