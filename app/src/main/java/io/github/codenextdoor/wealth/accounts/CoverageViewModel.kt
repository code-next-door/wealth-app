package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Coverage
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** One account's statements in the year shown. */
data class CoverageRow(
    val accountId: Long,
    val name: String,
    val isLiability: Boolean,
    val currencyCode: String,
    val decimals: Int,
    val shareSymbol: String?,
    /** January to December; null where no balance is expected. Null when the account doesn't track balances. */
    val balances: List<Coverage.Mark?>?,
    /** Same for imported transactions; null for accounts without any. */
    val transactions: List<Coverage.Mark?>?,
    /** The year's months missing a balance or transactions, oldest first. */
    val missing: List<YearMonth>,
    /** The year's months missing a balance: what "Add balance" offers. */
    val missingBalances: List<YearMonth>,
)

data class CoverageUiState(
    val isLoading: Boolean = true,
    val year: Int = 0,
    /** The years with expected months; empty when nothing is expected yet. */
    val years: IntRange = IntRange.EMPTY,
    /** Accounts with expected months in [year]. */
    val rows: List<CoverageRow> = emptyList(),
    val baseCurrency: String = "",
    val rateBook: RateBook = RateBook(emptyList()),
    val priceBook: PriceBook = PriceBook.EMPTY,
)

class CoverageViewModel(
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
    expenseRepository: ExpenseRepository,
    rateUpdater: RateUpdater,
    private val shareRepository: ShareRepository,
    priceUpdater: PriceUpdater,
    /** Accounts whose value the app works out (calculated loans, growing pensions, houses). */
    calculatedAccounts: Flow<Set<Long>>,
    today: StateFlow<LocalDate>,
) : ViewModel() {

    val rateLookups = RateLookups(rateUpdater, viewModelScope)
    val priceLookups = RateLookups.forPrices(priceUpdater, viewModelScope)

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook, val prices: PriceBook)

    /** Null: last month's year. */
    private val selectedYear = MutableStateFlow<Int?>(null)

    private val coverage = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        expenseRepository.allExpenses,
        calculatedAccounts,
        today,
    ) { accounts, entries, expenses, calculated, day ->
        val lastMonth = YearMonth.from(day).minusMonths(1)
        Triple(accounts, Coverage.of(accounts, entries, expenses, calculated, lastMonth), lastMonth)
    }

    val uiState: StateFlow<CoverageUiState> = combine(
        coverage,
        catalogRepository.accountTypes,
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, shareRepository.prices, ::Money),
        selectedYear,
    ) { (accounts, coverage, lastMonth), types, (currencies, base, rates, prices), selected ->
        val accountsById = accounts.associateBy { it.id }
        val liabilityTypes = types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet()
        val decimals = currencies.associate { it.code to it.decimals }
        val firstYear = coverage.flatMap { listOfNotNull(it.balances, it.transactions) }.minOfOrNull { it.months.keys.min().year }
        val year = selected ?: lastMonth.year
        fun Coverage.Line.inYear() = (1..12).map { months[YearMonth.of(year, it)] }
        val rows = coverage.mapNotNull { item ->
            val account = accountsById[item.accountId] ?: return@mapNotNull null
            val balances = item.balances?.inYear()
            val transactions = item.transactions?.inYear()
            if (listOfNotNull(balances, transactions).flatten().all { it == null }) return@mapNotNull null
            CoverageRow(
                accountId = account.id,
                name = account.name,
                isLiability = account.accountTypeId in liabilityTypes,
                currencyCode = account.currencyCode,
                decimals = decimals[account.currencyCode] ?: 2,
                shareSymbol = account.shareSymbol,
                balances = balances,
                transactions = transactions,
                missing = item.missing.filter { it.year == year },
                missingBalances = item.balances?.months.orEmpty().filter { it.key.year == year && it.value == Coverage.Mark.MISSING }.keys.sorted(),
            )
        }
        CoverageUiState(
            isLoading = false,
            year = year,
            years = if (firstYear == null) IntRange.EMPTY else firstYear..lastMonth.year,
            rows = rows,
            baseCurrency = base,
            rateBook = rates,
            priceBook = prices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUiState())

    fun selectYear(year: Int) {
        selectedYear.value = year
    }

    /** Saves a missing month's balance, like "Add past balance" on the account. */
    fun addBalance(accountId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal? = null, price: PriceEntry? = null) {
        viewModelScope.launch {
            if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date, fetched = rate.fetched)
            if (price != null) shareRepository.setPrice(price.symbol, date, price.price, fetched = price.fetched)
            accountRepository.addHistoryEntry(accountId, date, balanceMinor, units)
        }
    }

    companion object {
        val Factory = appViewModelFactory {
            CoverageViewModel(
                it.accountRepository, it.catalogRepository, it.currencyRepository, it.expenseRepository, it.rateUpdater,
                it.shareRepository, it.priceUpdater, it.calculatedAccounts, it.today.date,
            )
        }
    }
}
