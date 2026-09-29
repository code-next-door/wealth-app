package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.formatUnits
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
    /** For accounts holding shares: the symbol and the shares that day ([balanceMinor] is the cash). */
    val shareSymbol: String? = null,
    val units: java.math.BigDecimal? = null,
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
    val priceBook: PriceBook = PriceBook.EMPTY,
)

class HistoryViewModel(
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
    rateUpdater: RateUpdater,
    private val shareRepository: ShareRepository,
    priceUpdater: PriceUpdater,
) : ViewModel() {

    /** Downloads share prices for entries of accounts holding shares. */
    val priceLookups = RateLookups.forPrices(priceUpdater, viewModelScope)

    /** Downloads the rate for each currency and date the edit dialog shows. */
    val rateLookups = RateLookups(rateUpdater, viewModelScope)

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook, val prices: PriceBook)

    private val filter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        catalogRepository.accountTypes,
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, shareRepository.prices, ::Money),
        filter,
    ) { accounts, entries, types, (currencies, base, rates, prices), selected ->
        val accountsById = accounts.associateBy { it.id }
        val liabilityTypes = types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet()
        val decimals = currencies.associate { it.code to it.decimals }
        val entryCount = entries.groupingBy { it.accountId }.eachCount()

        val rows = entries
            .filter { selected == null || it.accountId == selected }
            .mapNotNull { entry ->
                val account = accountsById[entry.accountId] ?: return@mapNotNull null
                val dec = decimals[account.currencyCode] ?: 2
                val isLiability = account.accountTypeId in liabilityTypes
                HistoryRow(
                    entryId = entry.id,
                    accountId = account.id,
                    accountName = account.name,
                    date = entry.date,
                    balanceMinor = entry.balanceMinor,
                    // Debts are stored as the amount owed; shown negative, as they count in net worth.
                    amountText = minorToDecimal(entry.balanceMinor, dec)
                        .let { if (isLiability) it.negate() else it }
                        .let { formatMoney(it, account.currencyCode, dec) }.let { cash ->
                        // "10 GOOG + $50.00" for accounts holding shares.
                        if (account.shareSymbol != null && entry.units != null) "${formatUnits(entry.units)} ${account.shareSymbol} + $cash" else cash
                    },
                    currencyCode = account.currencyCode,
                    decimals = dec,
                    isLiability = isLiability,
                    canDelete = (entryCount[account.id] ?: 0) > 1,
                    shareSymbol = account.shareSymbol,
                    units = entry.units,
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
            priceBook = prices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectAccount(accountId: Long?) {
        filter.value = accountId
    }

    fun updateEntry(entryId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: java.math.BigDecimal? = null, price: PriceEntry? = null) {
        viewModelScope.launch {
            if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date, fetched = rate.fetched)
            if (price != null) shareRepository.setPrice(price.symbol, date, price.price, fetched = price.fetched)
            accountRepository.updateHistoryEntry(entryId, date, balanceMinor, units)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { accountRepository.deleteHistoryEntry(entryId) }
    }

    companion object {
        val Factory = appViewModelFactory {
            HistoryViewModel(it.accountRepository, it.catalogRepository, it.currencyRepository, it.rateUpdater, it.shareRepository, it.priceUpdater)
        }
    }
}
