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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** One found transaction: its day and the row as the Spending tab shows it. */
data class SearchResult(val date: LocalDate, val row: ExpenseRow)

/** Money out and in for one currency, formatted; [inText] null when nothing came in. */
data class SearchTotal(val outText: String, val inText: String?)

data class SearchUiState(
    val isLoading: Boolean = true,
    /** False until something is typed. */
    val searched: Boolean = false,
    /** All matches; [results] holds the newest [SearchViewModel.MAX_ROWS] of them. */
    val count: Int = 0,
    val results: List<SearchResult> = emptyList(),
    val totals: List<SearchTotal> = emptyList(),
)

/**
 * Spending › Search: every transaction of every month, matched as [TransactionSearch]
 * describes, newest first, with totals per currency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    val query = TextFieldState()

    private val data = combine(
        expenseRepository.allExpenses,
        catalogRepository.expenseCategories,
        accountRepository.accounts,
        currencyRepository.currencies,
    ) { expenses, categories, accounts, currencies ->
        val categoryNames = categories.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        val decimals = currencies.associate { it.code to it.decimals }
        expenses.map { expense ->
            TransactionSearch.Candidate(
                expense = expense,
                decimals = decimals[expense.currencyCode] ?: 2,
                categoryName = expense.categoryId?.let(categoryNames::get),
                accountName = expense.accountId?.let(accountNames::get),
                partCategoryNames = expense.parts.mapNotNull { part -> part.categoryId?.let(categoryNames::get) },
            )
        }
    }

    val state: StateFlow<SearchUiState> = combine(
        snapshotFlow { query.text.toString() }.distinctUntilChanged(),
        data,
    ) { text, candidates -> text to candidates }
        .mapLatest { (text, candidates) -> search(text, candidates) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private fun search(text: String, candidates: List<TransactionSearch.Candidate>): SearchUiState {
        val query = TransactionSearch.parse(text) ?: return SearchUiState(isLoading = false)
        // The repository lists newest first.
        val found = candidates.filter(query::matches)
        val decimalsOf = found.associate { it.expense.currencyCode to it.decimals }
        fun money(minor: Long, code: String): String {
            val dec = decimalsOf[code] ?: 2
            return formatMoney(minorToDecimal(minor, dec), code, dec)
        }
        return SearchUiState(
            isLoading = false,
            searched = true,
            count = found.size,
            results = found.take(MAX_ROWS).map { candidate ->
                val expense = candidate.expense
                SearchResult(
                    date = expense.date,
                    row = ExpenseRow(
                        id = expense.id,
                        description = expense.description,
                        categoryName = candidate.categoryName,
                        accountName = candidate.accountName,
                        amountText = money(kotlin.math.abs(expense.amountMinor), expense.currencyCode),
                        baseAmountText = null,
                        moneyIn = expense.amountMinor < 0,
                    ),
                )
            },
            totals = TransactionSearch.totals(found.map { it.expense }).map { total ->
                SearchTotal(
                    outText = money(total.outMinor, total.currencyCode),
                    inText = total.inMinor.takeIf { it > 0 }?.let { money(it, total.currencyCode) },
                )
            },
        )
    }

    companion object {
        /** Rows listed at most; the count and totals still cover every match. */
        const val MAX_ROWS = 200

        val Factory = appViewModelFactory {
            SearchViewModel(it.expenseRepository, it.catalogRepository, it.accountRepository, it.currencyRepository)
        }
    }
}
