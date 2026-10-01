package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import io.github.codenextdoor.wealth.domain.Property
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.LoanBalance
import io.github.codenextdoor.wealth.domain.Loan
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.Vesting
import io.github.codenextdoor.wealth.domain.formatUnits
import io.github.codenextdoor.wealth.domain.holdingValue
import java.time.LocalDate
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountRow(
    val id: Long,
    val name: String,
    /** e.g. "Pillar 3a · Switzerland · UBS". */
    val details: String,
    /** Balance in the account's own currency. */
    val balanceText: String,
    /** Balance converted to the base currency; null when it's already in base currency. */
    val baseValueText: String?,
    /** Set when the balance can't be converted because a rate is missing. */
    val missingRateFor: String?,
    /** For accounts holding shares: "12.5 GOOG × $156.23". */
    val sharesText: String? = null,
    /** Set when the account holds shares but there's no price for them yet. */
    val missingPriceFor: String? = null,
    /** A house's loan while houses are left out of net worth: not in the totals. */
    val notInNetWorth: Boolean = false,
    /** Left out of net worth by the user's choice: not in the totals. */
    val leftOut: Boolean = false,
    /** A loan whose outstanding the app calculates. */
    val isCalculated: Boolean = false,
)

/** A stock grant on the Accounts tab; its value isn't part of net worth. */
data class GrantRow(
    val id: Long,
    val name: String,
    val symbol: String,
    /** Formatted unit counts, e.g. "36" of "48". */
    val unvestedUnits: String,
    val totalUnits: String,
    /** Unvested value in the base currency; null without a price or rate. */
    val valueText: String?,
    /** The next vest; null when fully vested. */
    val nextVestDate: LocalDate?,
    val nextVestUnits: String?,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val assets: List<AccountRow> = emptyList(),
    val liabilities: List<AccountRow> = emptyList(),
    /** Section totals in base currency (accounts without a rate are left out). */
    val assetsTotalText: String = "",
    val liabilitiesTotalText: String = "",
    val grants: List<GrantRow> = emptyList(),
    /** Total unvested value in base currency, when every grant can be valued. */
    val unvestedTotalText: String? = null,
)

class AccountsViewModel(
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    shareRepository: ShareRepository,
    houseRepository: HouseRepository,
    today: StateFlow<LocalDate>,
    /** Calculated loans: their rows show the outstanding worked out for today. */
    loans: Flow<List<Loan>> = flowOf(emptyList()),
) : ViewModel() {

    private data class Context(
        val houses: List<Property>,
        val housesCounted: Boolean,
        val today: LocalDate,
        val loans: List<Loan>,
        val entries: List<BalanceEntry>,
    )

    private val houses = combine(houseRepository.houses, houseRepository.inNetWorth, today, loans, accountRepository.balanceEntries, ::Context)

    private data class Money(
        val currencies: Map<String, Currency>,
        val base: String,
        val converter: CurrencyConverter,
        val prices: PriceBook,
        val grants: List<Grant>,
    )

    private val money = combine(
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
        currencyRepository.exchangeRates,
        shareRepository.prices,
        shareRepository.grants,
    ) { currencies, base, rates, prices, grants -> Money(currencies.associateBy { it.code }, base, CurrencyConverter(rates), prices, grants) }

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.accounts,
        catalogRepository.accountTypes,
        catalogRepository.countries,
        money,
        houses,
    ) { allAccounts, types, countries, (currencies, base, converter, prices, grants), (houseList, housesCounted, today, loans, entries) ->
        val outstanding = LoanBalance.outstanding(loans, entries, today)
        // Houses have their own tab.
        val houseIds = houseList.map { it.accountId }.toSet()
        val accounts = allAccounts.filter { it.id !in houseIds }
        val loansLeftOut = if (housesCounted) emptySet() else houseList.mapNotNull { it.loanAccountId }.toSet()
        val typesById = types.associateBy { it.id }
        val countryNames = countries.associate { it.id to it.name }
        val baseDecimals = currencies[base]?.decimals ?: 2

        val rows = accounts.map { account ->
            val type = typesById[account.accountTypeId]
            val decimals = currencies[account.currencyCode]?.decimals ?: 2
            val calculated = outstanding[account.id]
            val cash = minorToDecimal(calculated ?: account.balanceMinor, decimals)
            val symbol = account.shareSymbol
            val price = symbol?.let { prices.priceAt(it, today) }
            val valued = holdingValue(cash, account.units, price)
            val amount = valued ?: cash
            val inBase = if (account.currencyCode == base || valued == null) null else converter.convert(amount, account.currencyCode, base)
            val row = AccountRow(
                id = account.id,
                name = account.name,
                details = listOfNotNull(
                    type?.name,
                    account.countryId?.let(countryNames::get),
                    account.institution,
                ).joinToString(" · "),
                isCalculated = calculated != null,
                balanceText = formatMoney(amount, account.currencyCode, decimals),
                baseValueText = inBase?.let { formatMoney(it, base, baseDecimals) },
                missingRateFor = if (account.currencyCode != base && inBase == null && valued != null) account.currencyCode else null,
                sharesText = if (symbol != null && account.units != null && price != null) {
                    "${formatUnits(account.units)} $symbol × ${formatMoney(price, account.currencyCode, decimals)}"
                } else {
                    null
                },
                missingPriceFor = if (symbol != null && valued == null) symbol else null,
                notInNetWorth = account.id in loansLeftOut,
                leftOut = account.excludedFromNetWorth,
            )
            val counted = valued != null && account.id !in loansLeftOut && !account.excludedFromNetWorth
            Triple(type?.kind ?: AssetKind.ASSET, row, if (!counted) null else if (account.currencyCode == base) amount else inBase)
        }
        fun total(kind: AssetKind) = formatMoney(
            rows.filter { it.first == kind }.mapNotNull { it.third }.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
            base,
            baseDecimals,
        )
        val baseDecimalsForGrants = baseDecimals
        val grantValues = grants.map { grant ->
            val unvested = Vesting.unvested(grant, today)
            val value = prices.priceAt(grant.symbol, today)?.let { converter.convert(unvested * it, grant.currencyCode, base) }
            val next = Vesting.next(grant, today)
            GrantRow(
                id = grant.id,
                name = grant.name,
                symbol = grant.symbol,
                unvestedUnits = formatUnits(unvested),
                totalUnits = formatUnits(grant.totalUnits),
                valueText = value?.let { formatMoney(it, base, baseDecimalsForGrants) },
                nextVestDate = next?.date,
                nextVestUnits = next?.let { formatUnits(it.units) },
            ) to value
        }
        AccountsUiState(
            isLoading = false,
            grants = grantValues.map { it.first },
            unvestedTotalText = if (grantValues.isNotEmpty() && grantValues.all { it.second != null }) {
                formatMoney(grantValues.fold(java.math.BigDecimal.ZERO) { sum, g -> sum + g.second!! }, base, baseDecimals)
            } else {
                null
            },
            assets = rows.filter { it.first == AssetKind.ASSET }.map { it.second },
            liabilities = rows.filter { it.first == AssetKind.LIABILITY }.map { it.second },
            assetsTotalText = total(AssetKind.ASSET),
            liabilitiesTotalText = total(AssetKind.LIABILITY),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    companion object {
        val Factory = appViewModelFactory {
            AccountsViewModel(it.accountRepository, it.catalogRepository, it.currencyRepository, it.shareRepository, it.houseRepository, it.today.date, it.loanRepository.loans)
        }
    }
}
