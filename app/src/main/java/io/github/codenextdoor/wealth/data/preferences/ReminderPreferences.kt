package io.github.codenextdoor.wealth.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.codenextdoor.wealth.domain.BalanceReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth

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

    /** Monthly review: notification and Overview card in the first week of a month. */
    private val _reviewEnabled = MutableStateFlow(store.current[REVIEW] ?: true)
    val reviewEnabled: StateFlow<Boolean> = _reviewEnabled.asStateFlow()

    /** The last month whose review was announced (once per month). */
    val reviewNotified: YearMonth? get() = store.current[REVIEW_NOTIFIED]?.let(YearMonth::parse)

    /** The month whose Overview card "Not now" hid. */
    private val _reviewCardHidden = MutableStateFlow(store.current[REVIEW_CARD_HIDDEN]?.let(YearMonth::parse))
    val reviewCardHidden: StateFlow<YearMonth?> = _reviewCardHidden.asStateFlow()

    fun setReviewEnabled(on: Boolean) {
        store.edit { it[REVIEW] = on }
        _reviewEnabled.value = on
    }

    fun setReviewNotified(month: YearMonth) = store.edit { it[REVIEW_NOTIFIED] = month.toString() }

    fun hideReviewCard(month: YearMonth) {
        store.edit { it[REVIEW_CARD_HIDDEN] = month.toString() }
        _reviewCardHidden.value = month
    }

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
        val REVIEW = booleanPreferencesKey("monthly_review")
        val REVIEW_NOTIFIED = stringPreferencesKey("review_notified_month")
        val REVIEW_CARD_HIDDEN = stringPreferencesKey("review_card_hidden_month")
    }
}
