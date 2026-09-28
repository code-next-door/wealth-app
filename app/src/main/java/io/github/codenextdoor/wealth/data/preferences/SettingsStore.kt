package io.github.codenextdoor.wealth.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A small settings file (Jetpack DataStore: saved atomically, so a crash
 * mid-write can't corrupt it). Read once when created and kept in memory, so
 * the lock and theme are known before the first frame; each [edit] is on disk
 * before it returns (the wrong-PIN counter must survive the app being killed).
 * The disk work runs on a background thread.
 *
 * On first use it moves over the older SharedPreferences file of the same
 * [name] (and deletes that only once the move is saved). Only one store may be
 * open per file: `AppContainer` makes one each.
 */
class SettingsStore(context: Context, name: String) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, name)),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(name) },
    )

    @Volatile
    var current: Preferences = runBlocking(Dispatchers.IO) { dataStore.data.first() }
        private set

    fun edit(block: (MutablePreferences) -> Unit) {
        current = runBlocking(Dispatchers.IO) { dataStore.edit(block) }
    }

    /** Frees the file, e.g. for a test that opens it again. */
    fun close() = scope.cancel()
}
