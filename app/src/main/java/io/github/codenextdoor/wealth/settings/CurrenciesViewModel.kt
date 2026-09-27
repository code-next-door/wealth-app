package io.github.codenextdoor.wealth.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.domain.IsoCurrencies
import io.github.codenextdoor.wealth.domain.parsePositiveDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class CurrencyRow(
    val code: String,
    val name: String,
    val isBase: Boolean,
    /** Units of base currency per 1 unit of this currency; null if unknown. */
    val rateToBase: BigDecimal?,
    /** True when the rate was worked out via another currency, not entered directly. */
    val rateIsDerived: Boolean,
    /** Day of the latest direct rate, if there is one. */
    val rateDate: LocalDate? = null,
    /** That rate was downloaded rather than typed. */
    val rateFetched: Boolean = false,
)

/** The "Refresh rates" button's progress. */
enum class RatesRefresh { IDLE, RUNNING, DONE, OFFLINE }

data class CurrenciesUiState(
    val baseCurrency: String = "",
    val rows: List<CurrencyRow> = emptyList(),
)

enum class AddCurrencyResult { ADDED, INVALID_CODE, ALREADY_EXISTS }

class CurrenciesViewModel(
    private val repository: CurrencyRepository,
    private val rateUpdater: RateUpdater,
) : ViewModel() {

    val uiState: StateFlow<CurrenciesUiState> = combine(
        repository.currencies,
        repository.baseCurrency,
        repository.rateBook,
    ) { currencies, base, book ->
        val converter = book.current
        CurrenciesUiState(
            baseCurrency = base,
            rows = currencies.map { currency ->
                val isBase = currency.code == base
                val rate = if (isBase) null else converter.rate(currency.code, base)
                val point = if (isBase) null else book.pointAt(currency.code, base, LocalDate.MAX)
                CurrencyRow(
                    code = currency.code,
                    name = currency.name,
                    isBase = isBase,
                    rateToBase = rate,
                    rateIsDerived = rate != null && point == null,
                    rateDate = point?.date,
                    rateFetched = point?.fetched == true,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrenciesUiState())

    fun addCurrency(code: String): AddCurrencyResult {
        val currency = IsoCurrencies.lookup(code) ?: return AddCurrencyResult.INVALID_CODE
        if (uiState.value.rows.any { it.code == currency.code }) {
            return AddCurrencyResult.ALREADY_EXISTS
        }
        viewModelScope.launch { repository.addCurrency(currency) }
        return AddCurrencyResult.ADDED
    }

    private val _deleteBlocked = MutableStateFlow<String?>(null)

    /** Code of a currency that couldn't be deleted because accounts use it. */
    val deleteBlocked: StateFlow<String?> = _deleteBlocked.asStateFlow()

    fun deleteCurrency(code: String) {
        if (code == uiState.value.baseCurrency) return
        viewModelScope.launch {
            if (!repository.deleteCurrency(code)) _deleteBlocked.value = code
        }
    }

    fun dismissDeleteBlocked() {
        _deleteBlocked.value = null
    }

    fun setBaseCurrency(code: String) {
        viewModelScope.launch { repository.setBaseCurrency(code) }
    }

    /** Saves "1 [from] = [input] [to]". Returns false if [input] isn't a valid rate. */
    fun setRate(from: String, to: String, input: String): Boolean {
        val rate = parsePositiveDecimal(input) ?: return false
        viewModelScope.launch { repository.setRate(from, to, rate) }
        return true
    }

    private val _refresh = MutableStateFlow(RatesRefresh.IDLE)
    val refresh: StateFlow<RatesRefresh> = _refresh.asStateFlow()

    /** Downloads today's rates (and any missing for past balances). Rates the user typed stay. */
    fun refreshRates() {
        if (_refresh.value == RatesRefresh.RUNNING) return
        _refresh.value = RatesRefresh.RUNNING
        viewModelScope.launch {
            _refresh.value = if (rateUpdater.refresh().reachedSource) RatesRefresh.DONE else RatesRefresh.OFFLINE
        }
    }

    companion object {
        val Factory = appViewModelFactory { CurrenciesViewModel(it.currencyRepository, it.rateUpdater) }
    }
}
