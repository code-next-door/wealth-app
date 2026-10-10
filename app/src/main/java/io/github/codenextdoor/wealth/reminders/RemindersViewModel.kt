package io.github.codenextdoor.wealth.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.preferences.ReminderPreferences
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.BalanceReminders
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RemindersUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = true,
    val days: Int = BalanceReminders.DEFAULT_DAYS,
    /** Out of date, longest waiting first. */
    val stale: List<BalanceReminders.Stale> = emptyList(),
    /** The Overview card: on, something to update, and not hidden by "Not now". */
    val showCard: Boolean = false,
)

/** The Overview's "Not updated for a while" card and Settings › Reminders. */
class RemindersViewModel(
    accounts: Flow<List<Account>>,
    entries: Flow<List<BalanceEntry>>,
    /** Accounts whose value the app works out (calculated loans, growing pensions, houses). */
    calculated: Flow<Set<Long>>,
    private val prefs: ReminderPreferences,
    private val today: StateFlow<LocalDate>,
) : ViewModel() {

    val state: StateFlow<RemindersUiState> = combine(
        combine(accounts, entries, calculated) { a, e, c -> Triple(a, e, c) },
        prefs.enabled,
        prefs.days,
        prefs.snoozedUntil,
        today,
    ) { (accounts, entries, calculated), enabled, days, snoozedUntil, today ->
        val stale = BalanceReminders.stale(accounts, entries, calculated, today, days)
        RemindersUiState(
            isLoading = false,
            enabled = enabled,
            days = days,
            stale = stale,
            showCard = enabled && stale.isNotEmpty() && (snoozedUntil == null || today >= snoozedUntil),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setEnabled(on: Boolean) = prefs.setEnabled(on)

    fun setDays(days: Int) = prefs.setDays(days)

    /** Hides the card for a week. */
    fun notNow() = prefs.snoozeUntil(today.value.plusDays(SNOOZE_DAYS))

    companion object {
        private const val SNOOZE_DAYS = 7L

        val Factory = appViewModelFactory {
            RemindersViewModel(it.accountRepository.accounts, it.accountRepository.balanceEntries, it.calculatedAccounts, it.reminderPreferences, it.today.date)
        }
    }
}
