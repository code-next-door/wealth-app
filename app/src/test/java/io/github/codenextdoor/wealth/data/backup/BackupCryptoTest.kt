package io.github.codenextdoor.wealth.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoTest {

    // Few iterations keep the tests fast; the format stores the count.
    private fun encrypt(text: String, password: String) =
        BackupCrypto.encrypt(text.toByteArray(), password.toCharArray(), iterations = 1_000)

    @Test
    fun roundTrips() {
        val file = encrypt("hello backup", "correct horse")
        assertArrayEquals("hello backup".toByteArray(), BackupCrypto.decrypt(file, "correct horse".toCharArray()))
    }

    @Test
    fun contentIsNotReadableInTheFile() {
        val file = encrypt("SECRET-BALANCE-12345", "pw123456")
        assertFalse(String(file, Charsets.ISO_8859_1).contains("SECRET"))
    }

    @Test(expected = BackupCrypto.WrongPasswordOrDamaged::class)
    fun wrongPasswordFails() {
        BackupCrypto.decrypt(encrypt("x", "right-password"), "wrong-password".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordOrDamaged::class)
    fun tamperedContentFails() {
        val file = encrypt("some data", "pw123456")
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        BackupCrypto.decrypt(file, "pw123456".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordOrDamaged::class)
    fun tamperedHeaderFails() {
        val file = encrypt("some data", "pw123456")
        file[20] = (file[20] + 1).toByte() // inside the salt
        BackupCrypto.decrypt(file, "pw123456".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackup::class)
    fun otherFilesAreRejected() {
        BackupCrypto.decrypt("%PDF-1.7 not a backup at all, just some text".toByteArray(), "x".toCharArray())
    }
}
