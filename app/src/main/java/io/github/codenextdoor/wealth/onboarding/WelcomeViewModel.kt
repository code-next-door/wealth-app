package io.github.codenextdoor.wealth.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.IsoCurrencies
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.tour.Tour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WelcomeUiState(
    /** The app's currencies first, then every other known currency. */
    val currencies: List<Currency> = emptyList(),
    val baseCurrency: String = "",
)

/** The one welcome screen of a fresh install: base currency, then the tour or straight in. */
class WelcomeViewModel(
    private val currencyRepository: CurrencyRepository,
    private val onboarding: OnboardingPreferences,
    private val tour: Tour,
) : ViewModel() {

    /**
     * Whether first-run help is decided yet: on a fresh install the database is set up
     * first (a moment), and the app waits for that rather than showing the home screen
     * and then covering it. Known at once on every later start.
     */
    val settled: StateFlow<Boolean> get() = onboarding.settled

    val visible: StateFlow<Boolean> = combine(onboarding.settled, onboarding.welcomeDone) { settled, done -> settled && !done }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<WelcomeUiState> = combine(currencyRepository.currencies, currencyRepository.baseCurrency) { own, base ->
        val codes = own.map { it.code }.toSet()
        WelcomeUiState(own + IsoCurrencies.all().filter { it.code !in codes }, base)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    /** A currency the app doesn't have yet is added first (e.g. EUR for someone in Germany). */
    fun chooseBaseCurrency(code: String) {
        viewModelScope.launch {
            if (currencyRepository.currencies.first().none { it.code == code }) {
                IsoCurrencies.lookup(code)?.let { currencyRepository.addCurrency(it) } ?: return@launch
            }
            currencyRepository.setBaseCurrency(code)
        }
    }

    fun finish(showTour: Boolean) {
        onboarding.setWelcomeDone()
        if (showTour) tour.start()
    }

    companion object {
        val Factory = appViewModelFactory { WelcomeViewModel(it.currencyRepository, it.onboarding, it.tour) }
    }
}
