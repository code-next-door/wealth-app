package io.github.codenextdoor.wealth.expenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import io.github.codenextdoor.wealth.domain.formatMoneyShort
import io.github.codenextdoor.wealth.domain.formatPercent
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
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    /** False on the current month: there is nothing after it yet. */
    val canGoForward: Boolean = false,
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

/** One month in the year view: its spending (short form, null when none), and its shade. */
data class MonthTile(
    val month: YearMonth,
    val totalText: String?,
    /** 0–1 of the year's biggest month, for the tile's shade. */
    val fraction: Float,
    val isFuture: Boolean,
    val isSelected: Boolean,
)

/** The year view above the month: twelve months to jump between, and other years. */
data class YearUiState(
    val isLoading: Boolean = true,
    val year: Int = 0,
    /** The year's spending so far, short form; null when none. */
    val totalText: String? = null,
    val months: List<MonthTile> = emptyList(),
    /** Back to the year of the first expense; forward to the current year. */
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

class ExpensesViewModel(
    expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    private val today: StateFlow<LocalDate>,
) : ViewModel() {

    private val month = MutableStateFlow(thisMonth())
    private val filter = MutableStateFlow<Long?>(null)

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook)

    private val money = combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, ::Money)

    /** The year shown in the year view: follows the month, or browsed with its own arrows. */
    private val year = MutableStateFlow(thisMonth().year)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val yearExpenses = year.flatMapLatest { y ->
        expenseRepository.expensesBetween(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)).map { y to it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val twoMonths = month.flatMapLatest { m ->
        expenseRepository.expensesBetween(m.minusMonths(1).atDay(1), m.atEndOfMonth())
    }

    private val names = combine(catalogRepository.expenseCategories, accountRepository.accounts) { categories, accounts ->
        categories to accounts
    }

    val uiState: StateFlow<ExpensesUiState> = combine(
        combine(month, today) { m, t -> m to YearMonth.from(t) },
        filter,
        twoMonths,
        names,
        money,
    ) { (month, thisMonth), filter, expenses, (categories, accounts), money ->
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
            canGoForward = month < thisMonth,
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

    val yearState: StateFlow<YearUiState> = combine(
        yearExpenses,
        month,
        today,
        expenseRepository.earliestDate,
        money,
    ) { (year, expenses), month, today, earliest, money ->
        val thisMonth = YearMonth.from(today)
        val decimals = money.currencies.associate { it.code to it.decimals }
        val byMonth = expenses.groupBy { YearMonth.from(it.date) }
        val totals = (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            ym to byMonth[ym]?.let { SpendingSummary.of(it, money.rates, money.base) { code -> decimals[code] ?: 2 }.total }
        }
        val biggest = totals.mapNotNull { it.second }.maxOrNull()?.takeIf { it.signum() > 0 }
        val yearTotal = totals.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
        YearUiState(
            isLoading = false,
            year = year,
            totalText = yearTotal?.let { formatMoneyShort(it, money.base) },
            months = totals.map { (ym, total) ->
                MonthTile(
                    month = ym,
                    totalText = total?.let { formatMoneyShort(it, money.base) },
                    fraction = if (total == null || biggest == null) 0f else total.divide(biggest, CurrencyConverter.MATH).toFloat().coerceIn(0f, 1f),
                    isFuture = ym > thisMonth,
                    isSelected = ym == month,
                )
            },
            canGoBack = earliest != null && earliest.year < year,
            canGoForward = year < thisMonth.year,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearUiState())

    init {
        // The year view follows the month (arrows, a tapped month, an import, a new month).
        viewModelScope.launch { month.collect { year.value = it.year } }
        // Someone looking at the current month when a new one starts sees the new one.
        viewModelScope.launch {
            var current = thisMonth()
            today.map(YearMonth::from).distinctUntilChanged().collect { now ->
                if (month.value == current) month.value = now
                current = now
            }
        }
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
        month.update { if (it < thisMonth()) it.plusMonths(1) else it }
        filter.value = null
    }

    /** Jumps to a month from the year view (not one that hasn't started yet). */
    fun showMonth(target: YearMonth) {
        if (target > thisMonth()) return
        month.value = target
        filter.value = null
    }

    fun previousYear() {
        if (yearState.value.canGoBack) year.update { it - 1 }
    }

    fun nextYear() {
        if (year.value < thisMonth().year) year.update { it + 1 }
    }

    private fun thisMonth(): YearMonth = YearMonth.from(today.value)

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
                percentText = formatPercent(fraction.multiply(BigDecimal(100))),
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
            ExpensesViewModel(it.expenseRepository, it.catalogRepository, it.accountRepository, it.currencyRepository, it.today.date)
        }
    }
}
