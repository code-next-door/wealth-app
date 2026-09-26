package io.github.codenextdoor.wealth.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.SpendingSummary
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** A legend/filter entry: a category, "uncategorized", or the folded rest. */
data class CategorySlice(
    /** Category id; [UNCATEGORIZED] for none; null for the "Other" bucket. */
    val key: Long?,
    val label: String?,
    val amountText: String,
    val percentText: String,
    val fraction: Float,
    val colorSlot: Int,
)

data class ExpenseRow(
    val id: Long,
    val description: String,
    /** e.g. "Groceries · UBS card"; null category shows as uncategorized in the UI. */
    val categoryName: String?,
    val accountName: String?,
    val amountText: String,
    /** Converted to the base currency, when the expense is in another currency. */
    val baseAmountText: String?,
    val isRefund: Boolean,
)

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val totalText: String = "",
    /** Spending this month minus last month, pre-formatted with sign; null without data. */
    val vsPreviousText: String? = null,
    val spentMore: Boolean = false,
    val slices: List<CategorySlice> = emptyList(),
    /** Selected slice key (see [CategorySlice.key]); null shows everything. */
    val filter: Long? = null,
    val days: List<Pair<LocalDate, List<ExpenseRow>>> = emptyList(),
    val excludedCount: Int = 0,
    val hasExpenses: Boolean = false,
    val baseCurrency: String = "",
)

const val UNCATEGORIZED = -1L

class ExpensesViewModel(
    expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow<Long?>(null)

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val twoMonths = month.flatMapLatest { m ->
        expenseRepository.expensesBetween(m.minusMonths(1).atDay(1), m.atEndOfMonth())
    }

    private val names = combine(catalogRepository.expenseCategories, accountRepository.accounts) { categories, accounts ->
        categories to accounts
    }

    val uiState: StateFlow<ExpensesUiState> = combine(
        month,
        filter,
        twoMonths,
        names,
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, ::Money),
    ) { month, filter, expenses, (categories, accounts), money ->
        val decimals = money.currencies.associate { it.code to it.decimals }
        val decimalsOf = { code: String -> decimals[code] ?: 2 }
        val baseDecimals = decimalsOf(money.base)
        fun format(v: BigDecimal) = formatMoney(v, money.base, baseDecimals)

        val (current, previous) = expenses.partition { YearMonth.from(it.date) == month }
        val summary = SpendingSummary.of(current, money.rates, money.base, decimalsOf)
        val previousTotal = SpendingSummary.of(previous, money.rates, money.base, decimalsOf).total
        val categoryNames = categories.associate { it.id to it.name }
        val categoryOrder = categories.withIndex().associate { it.value.id to it.index }
        val accountNames = accounts.associate { it.id to it.name }

        val visible = current.filter { expense ->
            when (filter) {
                null -> true
                UNCATEGORIZED -> expense.categoryId == null
                else -> expense.categoryId == filter
            }
        }
        val rows = visible.map { expense ->
            val dec = decimalsOf(expense.currencyCode)
            val amount = minorToDecimal(expense.amountMinor, dec)
            ExpenseRow(
                id = expense.id,
                description = expense.description,
                categoryName = expense.categoryId?.let(categoryNames::get),
                accountName = expense.accountId?.let(accountNames::get),
                amountText = formatMoney(amount, expense.currencyCode, dec),
                baseAmountText = if (expense.currencyCode == money.base) {
                    null
                } else {
                    money.rates.converterAt(expense.date).convert(amount, expense.currencyCode, money.base)?.let(::format)
                },
                isRefund = expense.amountMinor < 0,
            )
        }
        val days = visible.zip(rows).groupBy({ it.first.date }, { it.second }).toList()

        ExpensesUiState(
            isLoading = false,
            month = month,
            totalText = format(summary.total),
            vsPreviousText = if (previous.isEmpty() || current.isEmpty()) {
                null
            } else {
                val diff = summary.total - previousTotal
                (if (diff.signum() < 0) "−" else "+") + format(diff.abs())
            },
            spentMore = summary.total > previousTotal,
            slices = slicesFor(summary, categoryNames, categoryOrder, ::format),
            filter = filter,
            days = days,
            excludedCount = summary.excludedCount,
            hasExpenses = current.isNotEmpty(),
            baseCurrency = money.base,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    init {
        // After an import, jump to the month it covers.
        viewModelScope.launch {
            expenseRepository.showMonthRequest.collect { requested ->
                if (requested != null) {
                    month.value = requested
                    filter.value = null
                    expenseRepository.showMonthRequest.value = null
                }
            }
        }
    }

    fun previousMonth() {
        month.update { it.minusMonths(1) }
        filter.value = null
    }

    fun nextMonth() {
        month.update { it.plusMonths(1) }
        filter.value = null
    }

    /** Tapping the selected slice again clears the filter. */
    fun toggleFilter(key: Long?) {
        filter.update { if (it == key) null else key }
    }

    /**
     * Largest categories first; beyond six the smallest fold into "Other".
     * Colors follow each category's position in settings, so they stay put.
     */
    private fun slicesFor(
        summary: SpendingSummary,
        names: Map<Long, String>,
        order: Map<Long, Int>,
        format: (BigDecimal) -> String,
    ): List<CategorySlice> {
        val positive = summary.byCategory.filterValues { it.signum() > 0 }
        val total = positive.values.fold(BigDecimal.ZERO, BigDecimal::add)
        if (total.signum() <= 0) return emptyList()
        val sorted = positive.entries.sortedByDescending { it.value }
        val shown = if (sorted.size > MAX_SLICES) sorted.take(MAX_SLICES - 1) else sorted
        val rest = sorted.drop(shown.size)
        val slots = shown.mapNotNull { it.key }.sortedBy { order[it] ?: Int.MAX_VALUE }.withIndex().associate { it.value to it.index }

        fun slice(key: Long?, label: String?, value: BigDecimal, slot: Int): CategorySlice {
            val fraction = value.divide(total, CurrencyConverter.MATH)
            return CategorySlice(
                key = key,
                label = label,
                amountText = format(value),
                percentText = fraction.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "%",
                fraction = fraction.toFloat(),
                colorSlot = slot,
            )
        }
        return shown.map { (id, value) ->
            if (id == null) slice(UNCATEGORIZED, null, value, -1) else slice(id, names[id], value, slots.getValue(id))
        } + if (rest.isEmpty()) emptyList() else listOf(slice(null, null, rest.fold(BigDecimal.ZERO) { s, e -> s + e.value }, -1))
    }

    companion object {
        private const val MAX_SLICES = 6

        val Factory = appViewModelFactory {
            ExpensesViewModel(it.expenseRepository, it.catalogRepository, it.accountRepository, it.currencyRepository)
        }
    }
}
