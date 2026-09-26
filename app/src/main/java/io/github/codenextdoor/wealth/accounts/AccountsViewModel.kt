package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.AssetKind
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
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val assets: List<AccountRow> = emptyList(),
    val liabilities: List<AccountRow> = emptyList(),
)

class AccountsViewModel(
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val money = combine(
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
        currencyRepository.exchangeRates,
    ) { currencies, base, rates -> Triple(currencies.associateBy { it.code }, base, CurrencyConverter(rates)) }

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.accounts,
        catalogRepository.accountTypes,
        catalogRepository.countries,
        money,
    ) { accounts, types, countries, (currencies, base, converter) ->
        val typesById = types.associateBy { it.id }
        val countryNames = countries.associate { it.id to it.name }
        val baseDecimals = currencies[base]?.decimals ?: 2

        val rows = accounts.map { account ->
            val type = typesById[account.accountTypeId]
            val decimals = currencies[account.currencyCode]?.decimals ?: 2
            val amount = minorToDecimal(account.balanceMinor, decimals)
            val inBase = if (account.currencyCode == base) null else converter.convert(amount, account.currencyCode, base)
            val row = AccountRow(
                id = account.id,
                name = account.name,
                details = listOfNotNull(
                    type?.name,
                    account.countryId?.let(countryNames::get),
                    account.institution,
                ).joinToString(" · "),
                balanceText = formatMoney(amount, account.currencyCode, decimals),
                baseValueText = inBase?.let { formatMoney(it, base, baseDecimals) },
                missingRateFor = if (account.currencyCode != base && inBase == null) account.currencyCode else null,
            )
            (type?.kind ?: AssetKind.ASSET) to row
        }
        AccountsUiState(
            isLoading = false,
            assets = rows.filter { it.first == AssetKind.ASSET }.map { it.second },
            liabilities = rows.filter { it.first == AssetKind.LIABILITY }.map { it.second },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    companion object {
        val Factory = appViewModelFactory {
            AccountsViewModel(it.accountRepository, it.catalogRepository, it.currencyRepository)
        }
    }
}
