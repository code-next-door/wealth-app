package io.github.codenextdoor.wealth.data.preferences

import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.codenextdoor.wealth.security.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Settings shows about automatic backups. */
data class AutoBackupState(
    val on: Boolean = false,
    /** The one file each backup replaces (picked once, e.g. in Google Drive). */
    val file: Uri? = null,
    val lastSuccess: Long? = null,
    /** Since when backups fail; null while they work. */
    val failedAt: Long? = null,
)

/**
 * Automatic backup settings (DataStore "auto_backup"). The password is kept only sealed
 * by the phone's key ([SecretBox]), so backups can be made without asking, and is
 * forgotten when automatic backups are switched off.
 */
class AutoBackupPreferences(internal val store: SettingsStore, private val box: SecretBox) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    /** A fingerprint of the data last backed up, to skip days when nothing changed. */
    val lastHash: String? get() = store.current[HASH]

    /** Throws if the phone lost the key it's sealed with. */
    fun password(): CharArray? = store.current[PASSWORD]?.let { String(box.open(it), Charsets.UTF_8).toCharArray() }

    fun turnOn(file: Uri, password: CharArray) = update {
        it.clear()
        it[ON] = true
        it[FILE] = file.toString()
        it[PASSWORD] = seal(password)
    }

    fun turnOff() = update { it.clear() }

    /** Another file: the next backup writes there whatever changed. */
    fun setFile(file: Uri) = update {
        it[FILE] = file.toString()
        it.remove(HASH)
        it.remove(FAILED_AT)
    }

    fun setPassword(password: CharArray) = update {
        it[PASSWORD] = seal(password)
        it.remove(HASH)
    }

    fun recordSuccess(time: Long, hash: String) = update {
        it[LAST_SUCCESS] = time
        it[HASH] = hash
        it.remove(FAILED_AT)
    }

    /** Keeps the first failure's time while they go on. */
    fun recordFailure(time: Long) = update { if (it[FAILED_AT] == null) it[FAILED_AT] = time }

    private fun seal(password: CharArray): String {
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        return try {
            box.seal(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun update(block: (MutablePreferences) -> Unit) {
        store.edit(block)
        _state.value = read()
    }

    private fun read(): AutoBackupState {
        val prefs = store.current
        return AutoBackupState(
            on = prefs[ON] ?: false,
            file = prefs[FILE]?.let(Uri::parse),
            lastSuccess = prefs[LAST_SUCCESS],
            failedAt = prefs[FAILED_AT],
        )
    }

    private companion object {
        val ON = booleanPreferencesKey("on")
        val FILE = stringPreferencesKey("file")
        val PASSWORD = stringPreferencesKey("sealed_password")
        val HASH = stringPreferencesKey("last_hash")
        val LAST_SUCCESS = longPreferencesKey("last_success")
        val FAILED_AT = longPreferencesKey("failed_at")
    }
}
