package io.github.codenextdoor.wealth.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.IsoCurrencies
import io.github.codenextdoor.wealth.domain.parsePositiveDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class CurrencyRow(
    val code: String,
    val name: String,
    val isBase: Boolean,
    /** Units of base currency per 1 unit of this currency; null if unknown. */
    val rateToBase: BigDecimal?,
    /** True when the rate was worked out via another currency, not entered directly. */
    val rateIsDerived: Boolean,
)

data class CurrenciesUiState(
    val baseCurrency: String = "",
    val rows: List<CurrencyRow> = emptyList(),
)

enum class AddCurrencyResult { ADDED, INVALID_CODE, ALREADY_EXISTS }

class CurrenciesViewModel(private val repository: CurrencyRepository) : ViewModel() {

    val uiState: StateFlow<CurrenciesUiState> = combine(
        repository.currencies,
        repository.baseCurrency,
        repository.exchangeRates,
    ) { currencies, base, rates ->
        val converter = CurrencyConverter(rates)
        val enteredPairs = rates.map { setOf(it.from, it.to) }.toSet()
        CurrenciesUiState(
            baseCurrency = base,
            rows = currencies.map { currency ->
                val isBase = currency.code == base
                val rate = if (isBase) null else converter.rate(currency.code, base)
                CurrencyRow(
                    code = currency.code,
                    name = currency.name,
                    isBase = isBase,
                    rateToBase = rate,
                    rateIsDerived = rate != null && setOf(currency.code, base) !in enteredPairs,
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

    fun deleteCurrency(code: String) {
        if (code == uiState.value.baseCurrency) return
        viewModelScope.launch { repository.deleteCurrency(code) }
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

    companion object {
        val Factory = appViewModelFactory { CurrenciesViewModel(it.currencyRepository) }
    }
}
