package io.github.codenextdoor.wealth.security

import java.util.Base64

/** A stand-in for the Keystore-sealed secret: reversible, but never the plain text; [lost] as if the key were gone. */
class FakeSecretBox : SecretBox {
    var lost = false

    override fun seal(secret: ByteArray): String = "sealed:" + Base64.getEncoder().encodeToString(secret.reversedArray())

    override fun open(sealed: String): ByteArray {
        if (lost) throw IllegalStateException("key gone")
        return Base64.getDecoder().decode(sealed.removePrefix("sealed:")).reversedArray()
    }
}
