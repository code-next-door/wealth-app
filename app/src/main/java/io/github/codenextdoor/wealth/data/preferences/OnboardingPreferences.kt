package io.github.codenextdoor.wealth.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * First-run help (welcome, getting-started checklist). Shown only on a fresh
 * install: [settle] decides once, at the first start after this feature
 * arrived, from whether the database was brand new. People updating the app
 * (with their data) never see it. Nothing shows until settled.
 */
class OnboardingPreferences(private val store: SettingsStore) {

    private val _settled = MutableStateFlow(store.current[SETTLED] ?: false)
    val settled: StateFlow<Boolean> = _settled.asStateFlow()

    private val _welcomeDone = MutableStateFlow(store.current[WELCOME_DONE] ?: false)
    val welcomeDone: StateFlow<Boolean> = _welcomeDone.asStateFlow()

    private val _checklistDismissed = MutableStateFlow(store.current[CHECKLIST_DISMISSED] ?: false)
    val checklistDismissed: StateFlow<Boolean> = _checklistDismissed.asStateFlow()

    /** A backup was saved (a getting-started step). */
    private val _backupMade = MutableStateFlow(store.current[BACKUP_MADE] ?: false)
    val backupMade: StateFlow<Boolean> = _backupMade.asStateFlow()

    /** Once: a fresh install gets the first-run help; an existing one is treated as set up. */
    fun settle(freshInstall: Boolean) {
        if (_settled.value) return
        if (freshInstall) set(welcomeDone = false, checklistDismissed = false) else set(welcomeDone = true, checklistDismissed = true)
    }

    fun setWelcomeDone() = set(welcomeDone = true)

    fun setChecklistDismissed(dismissed: Boolean) = set(checklistDismissed = dismissed)

    fun setBackupMade() {
        store.edit { it[BACKUP_MADE] = true }
        _backupMade.value = true
    }

    /** As after the first-run help was finished (device tests start from here). */
    fun skipAll() = set(welcomeDone = true, checklistDismissed = true)

    /** As on a fresh install (device tests of the first-run help). */
    fun startOver() {
        store.edit { it[BACKUP_MADE] = false }
        _backupMade.value = false
        set(welcomeDone = false, checklistDismissed = false)
    }

    private fun set(welcomeDone: Boolean = _welcomeDone.value, checklistDismissed: Boolean = _checklistDismissed.value) {
        store.edit {
            it[SETTLED] = true
            it[WELCOME_DONE] = welcomeDone
            it[CHECKLIST_DISMISSED] = checklistDismissed
        }
        _settled.value = true
        _welcomeDone.value = welcomeDone
        _checklistDismissed.value = checklistDismissed
    }

    private companion object {
        val SETTLED = booleanPreferencesKey("settled")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        val CHECKLIST_DISMISSED = booleanPreferencesKey("checklist_dismissed")
        val BACKUP_MADE = booleanPreferencesKey("backup_made")
    }
}
