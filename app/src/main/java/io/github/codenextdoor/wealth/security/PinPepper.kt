package io.github.codenextdoor.wealth.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Mixes a secret that never leaves this phone into the PIN's hash, so a copy of
 * the app's files isn't enough to try all PINs on a computer.
 */
interface PinPepper {
    /** HMAC of [data] with the phone's key. [createKey] only when storing a new PIN. */
    fun mac(data: ByteArray, createKey: Boolean = false): ByteArray

    /** The key is gone or the secure storage fails: the PIN can't be checked. */
    class Unavailable(cause: Throwable?) : IllegalStateException("The PIN key isn't available", cause)
}

/**
 * The key is an HMAC-SHA256 key in the Android Keystore (secure hardware where the
 * phone has it). It can be used, but never read out, and it needs no fingerprint.
 */
class KeystorePinPepper(private val alias: String = "wealth_pin_pepper") : PinPepper {

    override fun mac(data: ByteArray, createKey: Boolean): ByteArray = try {
        // The Keystore reads and writes files: on a background thread, the caller waits (a few ms),
        // as SettingsStore does, so the main thread never touches the disk (StrictMode).
        runBlocking(Dispatchers.IO) {
            Mac.getInstance(ALGORITHM).run {
                init(key(createKey))
                doFinal(data)
            }
        }
    } catch (e: PinPepper.Unavailable) {
        throw e
    } catch (e: Exception) {
        throw PinPepper.Unavailable(e)
    }

    private fun key(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        // Never make a new key to check an old PIN: it would make the right PIN look wrong.
        if (!create) throw PinPepper.Unavailable(null)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALGORITHM = "HmacSHA256"
    }
}
