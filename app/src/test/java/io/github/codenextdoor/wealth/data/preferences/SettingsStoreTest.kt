package io.github.codenextdoor.wealth.data.preferences

import androidx.core.content.edit
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "settings_${System.nanoTime()}"

    @Test
    fun movesTheOldSharedPreferencesFileOverWithTheirTypes() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
            putString("wrapped_passphrase", "c29tZSB3cmFwcGVkIGtleQ==")
            putInt("count", 3)
            putLong("until", 1_790_000_000_000)
            putBoolean("on", true)
        }

        val store = SettingsStore(context, name)
        assertEquals("c29tZSB3cmFwcGVkIGtleQ==", store.current[stringPreferencesKey("wrapped_passphrase")])
        assertEquals(3, store.current[intPreferencesKey("count")])
        assertEquals(1_790_000_000_000, store.current[longPreferencesKey("until")])
        assertEquals(true, store.current[booleanPreferencesKey("on")])
        assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())

        // Opened again (next app start): still there, not moved twice.
        store.close()
        assertEquals("c29tZSB3cmFwcGVkIGtleQ==", SettingsStore(context, name).current[stringPreferencesKey("wrapped_passphrase")])
    }

    @Test
    fun changesAreOnDiskWhenEditReturns() {
        val store = SettingsStore(context, name)
        store.edit { it[intPreferencesKey("count")] = 7 }
        assertEquals(7, store.current[intPreferencesKey("count")])
        store.close()
        assertEquals(7, SettingsStore(context, name).current[intPreferencesKey("count")])
    }

    @Test
    fun aMissingDatabaseKeyIsNeverReplacedWhileADatabaseExists() {
        val store = SettingsStore(context, name)
        val manager = DatabaseKeyManager(store) { true }
        val failure = runCatching { manager.getOrCreatePassphrase() }.exceptionOrNull()
        assertTrue(failure.toString(), failure is DatabaseKeyManager.MissingKey)
        assertTrue(store.current.asMap().isEmpty()) // nothing was stored
    }
}
