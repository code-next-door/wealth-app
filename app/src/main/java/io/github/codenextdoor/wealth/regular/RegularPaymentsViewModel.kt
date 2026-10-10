package io.github.codenextdoor.wealth.regular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.RegularPayments
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One regular payment as listed. */
data class RegularPaymentRow(
    val shop: String,
    /** The charge, e.g. "CHF 17.90". */
    val amountText: String,
    val cadence: RegularPayments.Cadence,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    /** A year of it in the base currency; null without a rate. */
    val yearlyText: String?,
)

data class RegularPaymentsUiState(
    val isLoading: Boolean = true,
    val active: List<RegularPaymentRow> = emptyList(),
    val stopped: List<RegularPaymentRow> = emptyList(),
    val hidden: List<RegularPaymentRow> = emptyList(),
    /** The active ones over a year, and a month of that, in the base currency. */
    val yearlyTotalText: String = "",
    val monthlyTotalText: String = "",
)

/** Spending › Regular payments (and the Spending tab's card). */
class RegularPaymentsViewModel(
    private val expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    today: StateFlow<LocalDate>,
) : ViewModel() {

    private val money = combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, ::Triple)

    val state: StateFlow<RegularPaymentsUiState> = combine(
        combine(expenseRepository.allExpenses, catalogRepository.expenseCategories, expenseRepository.rules, ::Triple),
        expenseRepository.hiddenRegularShops,
        money,
        today,
    ) { (expenses, categories, rules), hidden, (currencies, base, rates), today ->
        val categorizer = Categorizer(rules, categories.filter { it.isIncome }.map { it.id }.toSet())
        val result = RegularPayments.find(expenses, categories.associateBy { it.id }, categorizer, today, hidden)
        val decimals = currencies.associate { it.code to it.decimals }
        fun dec(code: String) = decimals[code] ?: 2
        val converter = rates.converterAt(today)
        fun yearlyInBase(p: RegularPayments.Payment): BigDecimal? =
            converter.convert(minorToDecimal(p.yearlyMinor, dec(p.currencyCode)), p.currencyCode, base)
        fun baseText(amount: BigDecimal) = formatMoney(amount.setScale(dec(base), RoundingMode.HALF_EVEN), base, dec(base))
        fun row(p: RegularPayments.Payment) = RegularPaymentRow(
            shop = p.shop,
            amountText = formatMoney(minorToDecimal(p.amountMinor, dec(p.currencyCode)), p.currencyCode, dec(p.currencyCode)),
            cadence = p.cadence,
            lastDate = p.lastDate,
            nextDate = p.nextDate,
            yearlyText = yearlyInBase(p)?.let(::baseText),
        )
        val yearly = result.active.mapNotNull(::yearlyInBase).fold(BigDecimal.ZERO, BigDecimal::add)
        RegularPaymentsUiState(
            isLoading = false,
            active = result.active.map(::row),
            stopped = result.stopped.map(::row),
            hidden = result.hidden.map(::row),
            yearlyTotalText = baseText(yearly),
            monthlyTotalText = baseText(yearly.divide(BigDecimal(12), 10, RoundingMode.HALF_EVEN)),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegularPaymentsUiState())

    /** "Not a regular payment": the shop moves to Hidden (kept with the data, in backups). */
    fun hide(shop: String) {
        viewModelScope.launch { expenseRepository.setRegularShopHidden(shop, hidden = true) }
    }

    fun show(shop: String) {
        viewModelScope.launch { expenseRepository.setRegularShopHidden(shop, hidden = false) }
    }

    companion object {
        val Factory = appViewModelFactory {
            RegularPaymentsViewModel(it.expenseRepository, it.catalogRepository, it.currencyRepository, it.today.date)
        }
    }
}
