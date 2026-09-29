package io.github.codenextdoor.wealth.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A software stand-in for the phone's Keystore key; [available] = false as if it were lost. */
class FakePinPepper(private val secret: String = "phone key") : PinPepper {
    var available = true

    override fun mac(data: ByteArray, createKey: Boolean): ByteArray {
        if (!available) throw PinPepper.Unavailable(null)
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            doFinal(data)
        }
    }
}
