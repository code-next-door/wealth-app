package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.TransactionSearch
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.math.RoundingMode
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.RateBook

/** One found transaction: its day and the row as the Spending tab shows it. */
data class SearchResult(val date: LocalDate, val row: ExpenseRow)

/** Money out and in for one currency, formatted; [inText] null when nothing came in. */
data class SearchTotal(val outText: String, val inText: String?)

/** A category or account to filter by. */
data class FilterOption(val id: Long, val name: String)

/** One month's bar: what it adds up to (base currency) and its height against the biggest month. */
data class MonthBar(val month: YearMonth, val amountText: String, val fraction: Float)

/** The last 12 months of what's found: money out, or money in for an income category. */
data class SearchChart(val bars: List<MonthBar>, val moneyIn: Boolean)

data class SearchUiState(
    val isLoading: Boolean = true,
    /** False until something is typed or a filter is set. */
    val searched: Boolean = false,
    /** All matches; [results] holds the newest [SearchViewModel.MAX_ROWS] of them. */
    val count: Int = 0,
    val results: List<SearchResult> = emptyList(),
    val totals: List<SearchTotal> = emptyList(),
    /** A category id, [UNCATEGORIZED], or null for any. */
    val categoryFilter: Long? = null,
    val accountFilter: Long? = null,
    /** Only this month's rows (a tapped bar); the chart still shows all months. */
    val month: YearMonth? = null,
    val categories: List<FilterOption> = emptyList(),
    val accounts: List<FilterOption> = emptyList(),
    val chart: SearchChart? = null,
)

/**
 * Spending › Search: every transaction of every month, matched as [TransactionSearch]
 * describes, newest first, with totals per currency. Category and account filters, and a
 * chart of the last 12 months whose bars pick a month. Opened from a category (Spending)
 * or a shop (a transaction) with that already set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    today: StateFlow<LocalDate>,
    initialText: String? = null,
    initialCategory: Long? = null,
) : ViewModel() {

    val query = TextFieldState(initialText.orEmpty())

    private data class Filters(val category: Long? = null, val account: Long? = null, val month: YearMonth? = null)

    private val filters = MutableStateFlow(Filters(category = initialCategory))

    private class Data(
        val candidates: List<TransactionSearch.Candidate>,
        val categories: List<ExpenseCategory>,
        val accounts: List<FilterOption>,
        val base: String,
        val rates: RateBook,
        val decimals: Map<String, Int>,
    )

    private val data = combine(
        combine(expenseRepository.allExpenses, catalogRepository.expenseCategories, accountRepository.accounts, ::Triple),
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
        currencyRepository.rateBook,
    ) { (expenses, categories, accounts), currencies, base, rates ->
        val categoryNames = categories.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        val decimals = currencies.associate { it.code to it.decimals }
        Data(
            candidates = expenses.map { expense ->
                TransactionSearch.Candidate(
                    expense = expense,
                    decimals = decimals[expense.currencyCode] ?: 2,
                    categoryName = expense.categoryId?.let(categoryNames::get),
                    accountName = expense.accountId?.let(accountNames::get),
                    partCategoryNames = expense.parts.mapNotNull { part -> part.categoryId?.let(categoryNames::get) },
                )
            },
            categories = categories,
            accounts = accounts.map { FilterOption(it.id, it.name) },
            base = base,
            rates = rates,
            decimals = decimals,
        )
    }

    val state: StateFlow<SearchUiState> = combine(
        snapshotFlow { query.text.toString() }.distinctUntilChanged(),
        filters,
        data,
        today,
    ) { text, filters, data, today -> search(text, filters, data, YearMonth.from(today)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(categoryFilter = initialCategory))

    /** A category id, [UNCATEGORIZED], or null for any. */
    fun setCategory(category: Long?) = filters.update { it.copy(category = category, month = null) }

    fun setAccount(account: Long?) = filters.update { it.copy(account = account, month = null) }

    /** Tapping the selected month again shows all months. */
    fun selectMonth(month: YearMonth) = filters.update { it.copy(month = if (it.month == month) null else month) }

    private fun search(text: String, filters: Filters, data: Data, thisMonth: YearMonth): SearchUiState {
        val categoryFilter = when (filters.category) {
            null -> null
            UNCATEGORIZED -> TransactionSearch.CategoryFilter.None
            else -> TransactionSearch.CategoryFilter.Is(filters.category)
        }
        val base = SearchUiState(
            isLoading = false,
            categoryFilter = filters.category,
            accountFilter = filters.account,
            month = filters.month,
            categories = data.categories.map { FilterOption(it.id, it.name) },
            accounts = data.accounts,
        )
        // The repository lists newest first.
        val all = TransactionSearch.find(data.candidates, TransactionSearch.parse(text), categoryFilter, filters.account) ?: return base
        val found = filters.month?.let { month -> all.filter { YearMonth.from(it.expense.date) == month } } ?: all
        fun decimalsOf(code: String) = data.decimals[code] ?: 2
        fun money(minor: Long, code: String): String = formatMoney(minorToDecimal(minor, decimalsOf(code)), code, decimalsOf(code))

        val moneyIn = (filters.category?.let { id -> data.categories.firstOrNull { it.id == id } })?.isIncome == true
        val months = TransactionSearch.lastMonths(thisMonth, CHART_MONTHS)
        val sums = TransactionSearch.monthly(all, months, moneyIn, ::decimalsOf) { amount, code, date ->
            data.rates.converterAt(date).convert(amount, code, data.base)
        }
        val biggest = sums.maxOrNull()?.takeIf { it.signum() > 0 }
        val baseDecimals = decimalsOf(data.base)
        val chart = SearchChart(
            bars = months.zip(sums) { month, sum ->
                MonthBar(
                    month = month,
                    amountText = formatMoney(sum.setScale(baseDecimals, RoundingMode.HALF_EVEN), data.base, baseDecimals),
                    fraction = biggest?.let { sum.divide(it, 4, RoundingMode.HALF_EVEN).toFloat() } ?: 0f,
                )
            },
            moneyIn = moneyIn,
        )
        return base.copy(
            searched = true,
            count = found.size,
            results = found.take(MAX_ROWS).map { hit ->
                val expense = hit.expense
                val split = hit.countedMinor != expense.amountMinor
                SearchResult(
                    date = expense.date,
                    row = ExpenseRow(
                        id = expense.id,
                        description = expense.description,
                        categoryName = hit.candidate.categoryName,
                        accountName = hit.candidate.accountName,
                        amountText = money(kotlin.math.abs(hit.countedMinor), expense.currencyCode),
                        baseAmountText = null,
                        moneyIn = hit.countedMinor < 0,
                        // Only a split's matching parts count: say of how much.
                        partOfText = if (split) money(kotlin.math.abs(expense.amountMinor), expense.currencyCode) else null,
                    ),
                )
            },
            totals = TransactionSearch.totals(found).map { total ->
                SearchTotal(
                    outText = money(total.outMinor, total.currencyCode),
                    inText = total.inMinor.takeIf { it > 0 }?.let { money(it, total.currencyCode) },
                )
            },
            chart = chart,
        )
    }

    companion object {
        /** Rows listed at most; the count and totals still cover every match. */
        const val MAX_ROWS = 200
        const val CHART_MONTHS = 12

        fun factory(text: String? = null, category: Long? = null) = appViewModelFactory {
            SearchViewModel(it.expenseRepository, it.catalogRepository, it.accountRepository, it.currencyRepository, it.today.date, text, category)
        }
    }
}
