package io.github.codenextdoor.wealth.data.backup

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts backups with a password the user chooses.
 *
 * File layout: "WLTHBK" | version (1 byte) | PBKDF2 iterations (4 bytes) |
 * salt (16) | IV (12) | AES-256-GCM ciphertext of the gzipped content.
 * The header is authenticated along with the content, so any change to the
 * file makes decryption fail instead of producing wrong data.
 */
object BackupCrypto {

    class WrongPasswordOrDamaged : Exception("Wrong password or damaged backup")
    class NotABackup : Exception("Not a Wealth backup file")

    private val MAGIC = "WLTHBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    /** OWASP's current recommendation for PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val HEADER_BYTES = 6 + 1 + 4 + SALT_BYTES + IV_BYTES

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(plain))
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER_BYTES + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        val buffer = ByteBuffer.wrap(file)
        buffer.position(MAGIC.size)
        val version = buffer.get()
        if (version != VERSION) throw NotABackup()
        val iterations = buffer.getInt()
        if (iterations !in 1..10_000_000) throw NotABackup()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(file, 0, HEADER_BYTES)
        val plain = try {
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordOrDamaged()
        }
        return gunzip(plain)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
