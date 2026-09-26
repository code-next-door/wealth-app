package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HistoryRow(
    val entryId: Long,
    val accountId: Long,
    val accountName: String,
    val date: LocalDate,
    val balanceMinor: Long,
    val amountText: String,
    val currencyCode: String,
    val decimals: Int,
    val isLiability: Boolean,
    /** The account's only entry can't be deleted (an account always has a balance). */
    val canDelete: Boolean,
)

data class HistoryAccount(val id: Long, val name: String)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val accounts: List<HistoryAccount> = emptyList(),
    /** Null shows every account. */
    val selectedAccountId: Long? = null,
    /** Newest month first; entries newest first within a month. */
    val months: List<Pair<YearMonth, List<HistoryRow>>> = emptyList(),
    val baseCurrency: String = "",
    val rateBook: RateBook = RateBook(emptyList()),
) {
    /** Units of base currency per 1 unit of [currency] known for [date]. */
    fun rateOn(currency: String, date: LocalDate) = rateBook.converterAt(date).rate(currency, baseCurrency)
}

class HistoryViewModel(
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook)

    private val filter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        catalogRepository.accountTypes,
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, ::Money),
        filter,
    ) { accounts, entries, types, (currencies, base, rates), selected ->
        val accountsById = accounts.associateBy { it.id }
        val liabilityTypes = types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet()
        val decimals = currencies.associate { it.code to it.decimals }
        val entryCount = entries.groupingBy { it.accountId }.eachCount()

        val rows = entries
            .filter { selected == null || it.accountId == selected }
            .mapNotNull { entry ->
                val account = accountsById[entry.accountId] ?: return@mapNotNull null
                val dec = decimals[account.currencyCode] ?: 2
                HistoryRow(
                    entryId = entry.id,
                    accountId = account.id,
                    accountName = account.name,
                    date = entry.date,
                    balanceMinor = entry.balanceMinor,
                    amountText = formatMoney(minorToDecimal(entry.balanceMinor, dec), account.currencyCode, dec),
                    currencyCode = account.currencyCode,
                    decimals = dec,
                    isLiability = account.accountTypeId in liabilityTypes,
                    canDelete = (entryCount[account.id] ?: 0) > 1,
                )
            }
            .sortedWith(compareByDescending<HistoryRow> { it.date }.thenBy { it.accountName.lowercase() })

        HistoryUiState(
            isLoading = false,
            accounts = accounts.map { HistoryAccount(it.id, it.name) },
            selectedAccountId = selected,
            months = rows.groupBy { YearMonth.from(it.date) }.toList(),
            baseCurrency = base,
            rateBook = rates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectAccount(accountId: Long?) {
        filter.value = accountId
    }

    fun updateEntry(entryId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?) {
        viewModelScope.launch {
            if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date)
            accountRepository.updateHistoryEntry(entryId, date, balanceMinor)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { accountRepository.deleteHistoryEntry(entryId) }
    }

    companion object {
        val Factory = appViewModelFactory {
            HistoryViewModel(it.accountRepository, it.catalogRepository, it.currencyRepository)
        }
    }
}
