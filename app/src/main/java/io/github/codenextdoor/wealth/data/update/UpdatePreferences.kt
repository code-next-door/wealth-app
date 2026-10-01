package io.github.codenextdoor.wealth.data.update

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import java.time.LocalDate

/** Whether to look for updates (on unless switched off), and the last day an answer came. */
class UpdatePreferences(private val store: SettingsStore) {

    val enabled: Boolean get() = store.current[ENABLED] ?: true

    fun setEnabled(on: Boolean) = store.edit { it[ENABLED] = on }

    val lastChecked: LocalDate? get() = store.current[LAST_CHECKED]?.let(LocalDate::ofEpochDay)

    fun setLastChecked(day: LocalDate) = store.edit { it[LAST_CHECKED] = day.toEpochDay() }

    private companion object {
        val ENABLED = booleanPreferencesKey("check_for_updates")
        val LAST_CHECKED = longPreferencesKey("last_checked_day")
    }
}
