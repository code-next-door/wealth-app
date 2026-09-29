package io.github.codenextdoor.wealth.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import io.github.codenextdoor.wealth.data.db.DatabaseRecovery
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Recovery with the real Keystore and SQLCipher, on this test's own files (never the
 * app's): a wrong or missing key is recognised, and setting the data aside lets a new
 * database open.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fileName = "recovery-device-test.db"
    private val databaseFile get() = context.getDatabasePath(fileName)
    private val stores = mutableListOf<SettingsStore>()
    private var setAsideFolder: File? = null

    private val keyStore = SettingsStore(context, "database_key_recovery_test_${System.nanoTime()}").also { stores += it }

    @After
    fun cleanUp() {
        context.deleteDatabase(fileName)
        setAsideFolder?.deleteRecursively() // only this test's folder
        stores.forEach { it.close() }
    }

    @Test
    fun aWrongOrMissingKeyIsRecognisedAndStartingOverOpensANewDatabase() {
        val keys = DatabaseKeyManager(keyStore) { databaseFile.exists() }
        WealthDatabase.createChecked(context, keys.getOrCreatePassphrase(), fileName).apply {
            runBlocking { settingsDao().put(SettingEntity("probe", "kept")) }
            close()
        }

        // Another key (as if the Keystore changed): SQLCipher can't read the file.
        val otherKey = DatabaseKeyManager(SettingsStore(context, "database_key_other_${System.nanoTime()}").also { stores += it }) { false }
        val wrong = runCatching { WealthDatabase.createChecked(context, otherKey.getOrCreatePassphrase(), fileName) }.exceptionOrNull()
        assertEquals(wrong.toString(), DatabaseState.Reason.WRONG_KEY, wrong?.let(DatabaseState::reasonFor))

        // The key gone while the file is there: never replaced silently.
        val recovery = DatabaseRecovery(context, keys, fileName)
        val oldKey = keys.setAsideKey()!!
        val missing = runCatching { keys.getOrCreatePassphrase() }.exceptionOrNull()
        assertEquals(DatabaseState.Reason.KEY_MISSING, missing?.let(DatabaseState::reasonFor))

        // Start over: the data is set aside (not deleted), a new key and database follow.
        keyStore.edit { it[stringPreferencesKey("wrapped_passphrase")] = oldKey } // put it back
        setAsideFolder = recovery.setAside()
        assertFalse(databaseFile.exists())
        assertTrue(File(setAsideFolder, fileName).length() > 0)
        assertEquals(oldKey, File(setAsideFolder, "wrapped-key.txt").readText())
        val fresh = WealthDatabase.createChecked(context, keys.getOrCreatePassphrase(), fileName)
        assertNull(runBlocking { fresh.settingsDao().get("probe") }) // an empty, working database
        fresh.close()
    }
}
