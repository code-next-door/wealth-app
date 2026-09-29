package io.github.codenextdoor.wealth.data.db

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "recovery-test.db"
    private val store = SettingsStore(context, "database_key_${System.nanoTime()}")
    private val keys = DatabaseKeyManager(store) { context.getDatabasePath(fileName).exists() }

    @Test
    fun setAsideMovesTheFilesAndTheKeyAndDeletesNothing() {
        val db = context.getDatabasePath(fileName).apply { parentFile!!.mkdirs(); writeText("encrypted data") }
        val wal = context.getDatabasePath("$fileName-wal").apply { writeText("wal") }
        store.edit { it[stringPreferencesKey("wrapped_passphrase")] = "old-wrapped-key" }

        val folder = DatabaseRecovery(context, keys, fileName).setAside(LocalDateTime.of(2026, 9, 29, 10, 0))

        assertFalse(db.exists() || wal.exists())
        assertEquals("encrypted data", File(folder, fileName).readText())
        assertEquals("wal", File(folder, "$fileName-wal").readText())
        assertEquals("old-wrapped-key", File(folder, "wrapped-key.txt").readText())
        assertTrue(folder.path.contains("unreadable-databases"))
        assertNull(store.current[stringPreferencesKey("wrapped_passphrase")]) // a new key may now be made
    }

    @Test
    fun aKeyThatCantBeUnlockedIsReportedAsSuch() {
        // No Keystore on the JVM: unwrapping fails like a lost Keystore key would.
        store.edit { it[stringPreferencesKey("wrapped_passphrase")] = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" }
        val failure = runCatching { keys.getOrCreatePassphrase() }.exceptionOrNull()
        assertTrue(failure.toString(), failure is DatabaseKeyManager.KeyUnavailable)
        assertEquals(DatabaseState.Reason.KEY_UNAVAILABLE, DatabaseState.reasonFor(failure!!))
    }
}
