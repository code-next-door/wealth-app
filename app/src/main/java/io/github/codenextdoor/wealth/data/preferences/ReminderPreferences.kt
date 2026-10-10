package io.github.codenextdoor.wealth.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.codenextdoor.wealth.domain.BalanceReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Reminders to update balances (see BalanceReminders): on unless switched off, after
 * how many days, and until when "Not now" hides the Overview card.
 */
class ReminderPreferences(private val store: SettingsStore) {

    private val _enabled = MutableStateFlow(store.current[ENABLED] ?: true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _days = MutableStateFlow(store.current[DAYS] ?: BalanceReminders.DEFAULT_DAYS)
    val days: StateFlow<Int> = _days.asStateFlow()

    private val _snoozedUntil = MutableStateFlow(store.current[SNOOZED_UNTIL]?.let(LocalDate::ofEpochDay))
    val snoozedUntil: StateFlow<LocalDate?> = _snoozedUntil.asStateFlow()

    fun setEnabled(on: Boolean) {
        store.edit { it[ENABLED] = on }
        _enabled.value = on
    }

    fun setDays(days: Int) {
        store.edit { it[DAYS] = days }
        _days.value = days
    }

    fun snoozeUntil(day: LocalDate) {
        store.edit { it[SNOOZED_UNTIL] = day.toEpochDay() }
        _snoozedUntil.value = day
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("balance_reminders")
        val DAYS = intPreferencesKey("reminder_days")
        val SNOOZED_UNTIL = longPreferencesKey("card_snoozed_until")
    }
}
