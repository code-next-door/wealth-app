package io.github.codenextdoor.wealth.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The automatic backup's password, sealed with a real Keystore key (this test's own alias, deleted after). */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretBoxTest {

    private val alias = "wealth_secret_box_test"

    @After
    fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @Test
    fun sealsAndOpensAndNothingWithoutTheKey() {
        val box = KeystoreSecretBox(alias)
        val sealed = box.seal("long password".toByteArray())
        assertFalse(sealed.contains("long password"))
        assertEquals("long password", String(box.open(sealed)))
        deleteKey() // as if the Keystore lost it
        assertThrows(IllegalStateException::class.java) { box.open(sealed) }
    }
}
