package io.github.codenextdoor.wealth.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GettingStartedUiState(
    /** Shown on a fresh install until every step is done or it's hidden. */
    val visible: Boolean = false,
    val done: Set<GettingStartedStep> = emptySet(),
)

class GettingStartedViewModel(
    accountRepository: AccountRepository,
    appLock: AppLock,
    private val onboarding: OnboardingPreferences,
) : ViewModel() {

    private val shown = combine(onboarding.settled, onboarding.checklistDismissed) { settled, dismissed -> settled && !dismissed }

    val uiState: StateFlow<GettingStartedUiState> = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        appLock.settings,
        onboarding.backupMade,
        shown,
    ) { accounts, entries, lock, backupMade, shown ->
        val done = GettingStarted.done(accounts, entries, lock.enabled, backupMade)
        GettingStartedUiState(visible = shown && done.size < GettingStartedStep.entries.size, done = done)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GettingStartedUiState())

    fun hide() = onboarding.setChecklistDismissed(true)

    companion object {
        val Factory = appViewModelFactory { GettingStartedViewModel(it.accountRepository, it.appLock, it.onboarding) }
    }
}
