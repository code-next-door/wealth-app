package io.github.codenextdoor.wealth.data

import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The database file on disk must be unreadable without the key. */
@RunWith(AndroidJUnit4::class)
class EncryptionTest {

    // Its own file, so the app's real database is never touched.
    private val fileName = "encryption-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        context.deleteDatabase(fileName)
    }

    @Test
    fun fileIsEncryptedAndNeedsTheRightKey() {
        val db = WealthDatabase.create(context, "correct key".toByteArray(), fileName)
        runBlocking { db.settingsDao().put(SettingEntity("probe", "SECRET-VALUE-123")) }
        db.close()

        val bytes = context.getDatabasePath(fileName).readBytes()
        assertFalse("plain SQLite header found", String(bytes.copyOf(16), Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        assertFalse("secret readable in file", String(bytes, Charsets.ISO_8859_1).contains("SECRET-VALUE-123"))

        val reopened = WealthDatabase.create(context, "correct key".toByteArray(), fileName)
        assertEquals("SECRET-VALUE-123", runBlocking { reopened.settingsDao().get("probe") })
        reopened.close()

        val wrong = WealthDatabase.create(context, "wrong key".toByteArray(), fileName)
        val failed = runCatching { runBlocking { wrong.settingsDao().get("probe") } }.isFailure
        wrong.close()
        assertTrue("opened with the wrong key", failed)
    }

    @Test
    fun keyManagerReturnsTheSameKeyEveryTime() {
        // Its own settings file: the app's real key stays untouched.
        val store = SettingsStore(context, "database_key_test")
        val first = DatabaseKeyManager(store) { false }.getOrCreatePassphrase()
        assertTrue(first.contentEquals(DatabaseKeyManager(store) { true }.getOrCreatePassphrase()))
        assertEquals(64, first.size) // 32 random bytes as hex
    }
}
