package io.github.codenextdoor.wealth.house

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.accounts.RateEntry
import io.github.codenextdoor.wealth.accounts.RateLookups
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.PropertyValue
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import io.github.codenextdoor.wealth.domain.parseNonNegativeDecimal
import io.github.codenextdoor.wealth.ui.FormState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.appViewModelFactoryWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** A house on the House tab. */
data class HouseRow(
    val accountId: Long,
    val name: String,
    /** Estimated value today, in the house's currency. */
    val valueText: String,
    /** The same in the base currency; null when it's already in base currency (or no rate). */
    val baseValueText: String?,
    val purchasePriceText: String,
    val purchaseDate: LocalDate,
    /** Growth since the purchase, per year, e.g. "7.2"; null right after buying. */
    val yearlyGainPercent: String?,
    /** The rate used after the latest value, e.g. "7". */
    val growthPercent: String,
    val lastValueDate: LocalDate,
    val loanName: String?,
    /** Value minus the loan, when there is one. */
    val equityText: String?,
)

data class HouseListUiState(
    val isLoading: Boolean = true,
    val inNetWorth: Boolean = true,
    val houses: List<HouseRow> = emptyList(),
)

class HouseListViewModel(
    private val houseRepository: HouseRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val money = combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook) { c, b, r -> Triple(c, b, r) }

    val uiState: StateFlow<HouseListUiState> = combine(
        combine(houseRepository.houses, houseRepository.inNetWorth) { h, i -> h to i },
        accountRepository.accounts,
        accountRepository.balanceEntries,
        money,
    ) { (houses, inNetWorth), accounts, entries, (currencies, base, rates) ->
        val today = LocalDate.now()
        val byId = accounts.associateBy { it.id }
        val decimals = currencies.associate { it.code to it.decimals }
        val history = entries.groupBy { it.accountId }
        HouseListUiState(
            isLoading = false,
            inNetWorth = inNetWorth,
            houses = houses.mapNotNull { house ->
                val account = byId[house.accountId] ?: return@mapNotNull null
                val dec = decimals[account.currencyCode] ?: 2
                val anchors = history[account.id].orEmpty().map { it.date to minorToDecimal(it.balanceMinor, dec) }
                val value = PropertyValue.at(anchors, house.growthPercent, today)
                val price = minorToDecimal(house.purchasePriceMinor, dec)
                val loan = house.loanAccountId?.let(byId::get)
                val loanAmount = loan?.let {
                    rates.current.convert(minorToDecimal(it.balanceMinor, decimals[it.currencyCode] ?: 2), it.currencyCode, account.currencyCode)
                }
                HouseRow(
                    accountId = account.id,
                    name = account.name,
                    valueText = formatMoney(value, account.currencyCode, dec),
                    baseValueText = if (account.currencyCode == base) null else {
                        rates.current.convert(value, account.currencyCode, base)?.let { formatMoney(it, base, decimals[base] ?: 2) }
                    },
                    purchasePriceText = formatMoney(price, account.currencyCode, dec),
                    purchaseDate = house.purchaseDate,
                    yearlyGainPercent = yearlyGain(price, value, house.purchaseDate, today),
                    growthPercent = house.growthPercent.stripTrailingZeros().toPlainString(),
                    lastValueDate = anchors.maxOfOrNull { it.first } ?: house.purchaseDate,
                    loanName = loan?.name,
                    equityText = loanAmount?.let { formatMoney(value - it, account.currencyCode, dec) },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseListUiState())

    fun setInNetWorth(value: Boolean) {
        viewModelScope.launch { houseRepository.setInNetWorth(value) }
    }

    /** Average yearly growth from the purchase to today, one decimal; null in the first month. */
    private fun yearlyGain(price: BigDecimal, value: BigDecimal, bought: LocalDate, today: LocalDate): String? {
        val days = ChronoUnit.DAYS.between(bought, today)
        if (days < 30 || price.signum() <= 0 || value.signum() <= 0) return null
        val rate = (value.toDouble() / price.toDouble()).pow(365.25 / days) - 1
        return BigDecimal(rate * 100).setScale(1, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
    }

    companion object {
        val Factory = appViewModelFactory {
            HouseListViewModel(it.houseRepository, it.accountRepository, it.currencyRepository)
        }
    }
}

/** The house form's typed fields (Compose owns the text). */
class HouseTextFields {
    val name = TextFieldState()
    val price = TextFieldState()
    val growth = TextFieldState()
}

data class HouseChoices(
    val currencyCode: String? = null,
    val countryId: Long? = null,
    val purchaseDate: LocalDate = LocalDate.now(),
    val loanAccountId: Long? = null,
    /** A new house made from an existing "Real estate" account. */
    val convertFrom: Long? = null,
    val showErrors: Boolean = false,
)

/** A known value of the house: the purchase or a valuation. */
data class ValuationRow(val id: Long, val date: LocalDate, val amountMinor: Long, val amountText: String, val isPurchase: Boolean)

data class HouseEditUiState(
    val isNew: Boolean = true,
    val isReady: Boolean = false,
    val isFinished: Boolean = false,
    val currencies: List<Currency> = emptyList(),
    val countries: List<Country> = emptyList(),
    /** Loan and mortgage accounts that can be linked. */
    val loans: List<Account> = emptyList(),
    /** "Real estate" accounts that aren't houses yet (for a new house). */
    val convertible: List<Account> = emptyList(),
    val currencyCode: String = "",
    val countryId: Long? = null,
    val purchaseDate: LocalDate = LocalDate.now(),
    val loanAccountId: Long? = null,
    val convertFrom: Long? = null,
    val nameError: Boolean = false,
    val priceError: Boolean = false,
    val growthError: Boolean = false,
    /** Newest first. Only for a saved house. */
    val valuations: List<ValuationRow> = emptyList(),
    val baseCurrency: String = "",
    val rateBook: RateBook = RateBook(emptyList()),
) {
    val decimals: Int get() = currencies.firstOrNull { it.code == currencyCode }?.decimals ?: 2
}

class HouseEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val houseRepository: HouseRepository,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
    rateUpdater: RateUpdater,
) : ViewModel() {

    /** The house's account; null for a new house. */
    private val accountId: Long? = savedStateHandle.get<Long>(ARG_ACCOUNT_ID)?.takeIf { it > 0 }

    val fields = HouseTextFields()
    private val choices = FormState(HouseChoices())
    private val status = MutableStateFlow(Status(isReady = false, isFinished = false))

    /** Downloads the exchange rate for a valuation's day, like other balance forms. */
    val rateLookups = RateLookups(rateUpdater, viewModelScope)

    /** Kept when editing, so saving doesn't clear them. */
    private var institution: String? = null
    private var note: String? = null

    internal data class Status(val isReady: Boolean, val isFinished: Boolean)

    internal data class Lists(
        val currencies: List<Currency>,
        val countries: List<Country>,
        val accounts: List<Account>,
        val liabilityTypes: Set<Long>,
        val realEstateTypes: Set<Long>,
        val houseAccounts: Set<Long>,
        val history: List<BalanceEntry>,
        val base: String,
        val rates: RateBook,
    )

    class Data internal constructor(internal val status: Status, internal val lists: Lists?)

    private val history = if (accountId == null) flowOf(emptyList()) else accountRepository.observeHistory(accountId)

    val data: StateFlow<Data> = combine(
        status,
        combine(currencyRepository.currencies, catalogRepository.countries, accountRepository.accounts) { c, co, a -> Triple(c, co, a) },
        combine(catalogRepository.accountTypes, houseRepository.houses) { t, h -> t to h },
        history,
        combine(currencyRepository.baseCurrency, currencyRepository.rateBook) { b, r -> b to r },
    ) { status, (currencies, countries, accounts), (types, houses), history, (base, rates) ->
        Data(
            status,
            Lists(
                currencies, countries, accounts,
                liabilityTypes = types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet(),
                realEstateTypes = types.filter { it.seedKey == REAL_ESTATE_SEED_KEY }.map { it.id }.toSet(),
                houseAccounts = houses.map { it.accountId }.toSet(),
                history = history,
                base = base,
                rates = rates,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(status.value, null))

    init {
        viewModelScope.launch {
            val house = accountId?.let { houseRepository.forAccount(it) }
            val account = accountId?.let { accountRepository.get(it) }
            if (accountId != null && (house == null || account == null)) {
                status.update { it.copy(isFinished = true) }
                return@launch
            }
            if (house == null || account == null) {
                val base = currencyRepository.baseCurrency.first()
                choices.update { it.copy(currencyCode = base) }
            } else {
                val decimals = currencyRepository.currencies.first().firstOrNull { it.code == account.currencyCode }?.decimals ?: 2
                fields.name.setTextAndPlaceCursorAtEnd(account.name)
                fields.price.setTextAndPlaceCursorAtEnd(minorToInputText(house.purchasePriceMinor, decimals))
                fields.growth.setTextAndPlaceCursorAtEnd(house.growthPercent.stripTrailingZeros().toPlainString())
                institution = account.institution
                note = account.note
                choices.value = HouseChoices(account.currencyCode, account.countryId, house.purchaseDate, house.loanAccountId)
            }
            status.update { it.copy(isReady = true) }
        }
    }

    fun uiState(data: Data = this.data.value): HouseEditUiState {
        val c = choices.value
        val lists = data.lists
        val decimals = lists?.currencies?.firstOrNull { it.code == c.currencyCode }?.decimals ?: 2
        val purchaseDate = c.purchaseDate
        return HouseEditUiState(
            isNew = accountId == null,
            isReady = data.status.isReady && lists != null,
            isFinished = data.status.isFinished,
            currencies = lists?.currencies.orEmpty(),
            countries = lists?.countries.orEmpty(),
            loans = lists?.accounts.orEmpty().filter { it.accountTypeId in lists!!.liabilityTypes },
            convertible = if (accountId != null) {
                emptyList()
            } else {
                lists?.accounts.orEmpty().filter { it.accountTypeId in lists!!.realEstateTypes && it.id !in lists.houseAccounts }
            },
            currencyCode = c.currencyCode.orEmpty(),
            countryId = c.countryId,
            purchaseDate = purchaseDate,
            loanAccountId = c.loanAccountId,
            convertFrom = c.convertFrom,
            nameError = c.showErrors && fields.name.text.isBlank(),
            priceError = c.showErrors && priceMinor(decimals) == null,
            growthError = c.showErrors && growth() == null,
            valuations = lists?.history.orEmpty().map { entry ->
                ValuationRow(
                    entry.id, entry.date, entry.balanceMinor,
                    formatMoney(minorToDecimal(entry.balanceMinor, decimals), c.currencyCode.orEmpty(), decimals),
                    isPurchase = entry.date == purchaseDate,
                )
            },
            baseCurrency = lists?.base.orEmpty(),
            rateBook = lists?.rates ?: RateBook(emptyList()),
        )
    }

    private fun priceMinor(decimals: Int): Long? = parseAmountToMinor(fields.price.text.toString(), decimals)?.takeIf { it > 0 }

    /** Blank counts as 0% (no growth assumed). */
    private fun growth(): BigDecimal? = fields.growth.text.toString().trim().ifEmpty { "0" }.let(::parseNonNegativeDecimal)?.takeIf { it <= BigDecimal(100) }

    fun onCurrencyChange(code: String) = choices.update { it.copy(currencyCode = code) }

    fun onCountryChange(id: Long?) = choices.update { it.copy(countryId = id) }

    fun onPurchaseDateChange(date: LocalDate) = choices.update { it.copy(purchaseDate = date) }

    fun onLoanChange(id: Long?) = choices.update { it.copy(loanAccountId = id) }

    /** Makes the new house from an existing "Real estate" account (keeping its history). */
    fun onConvert(id: Long?) {
        val account = id?.let { accountId -> data.value.lists?.accounts?.firstOrNull { it.id == accountId } }
        if (account != null) {
            fields.name.setTextAndPlaceCursorAtEnd(account.name)
            institution = account.institution
            note = account.note
        }
        choices.update {
            if (account == null) it.copy(convertFrom = null) else it.copy(convertFrom = account.id, currencyCode = account.currencyCode, countryId = account.countryId)
        }
    }

    fun save() {
        choices.update { it.copy(showErrors = true) }
        val c = choices.value
        val decimals = data.value.lists?.currencies?.firstOrNull { it.code == c.currencyCode }?.decimals ?: return
        val name = fields.name.text.toString().trim()
        val price = priceMinor(decimals) ?: return
        val growth = growth() ?: return
        val currency = c.currencyCode ?: return
        if (name.isEmpty()) return
        viewModelScope.launch {
            houseRepository.save(
                HouseDetails(
                    accountId = accountId ?: c.convertFrom ?: 0,
                    name = name,
                    currencyCode = currency,
                    countryId = c.countryId,
                    purchasePriceMinor = price,
                    purchaseDate = c.purchaseDate,
                    growthPercent = growth,
                    loanAccountId = c.loanAccountId,
                    institution = institution,
                    note = note,
                ),
            )
            status.update { it.copy(isFinished = true) }
        }
    }

    fun delete() {
        val id = accountId ?: return
        viewModelScope.launch {
            houseRepository.delete(id)
            status.update { it.copy(isFinished = true) }
        }
    }

    fun addValuation(date: LocalDate, amountMinor: Long, rate: RateEntry?) {
        val id = accountId ?: return
        viewModelScope.launch {
            saveRate(rate, date)
            accountRepository.addHistoryEntry(id, date, amountMinor)
        }
    }

    fun editValuation(entryId: Long, date: LocalDate, amountMinor: Long, rate: RateEntry?) {
        viewModelScope.launch {
            saveRate(rate, date)
            accountRepository.updateHistoryEntry(entryId, date, amountMinor)
        }
    }

    fun deleteValuation(entryId: Long) {
        viewModelScope.launch { accountRepository.deleteHistoryEntry(entryId) }
    }

    private suspend fun saveRate(rate: RateEntry?, date: LocalDate) {
        if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date, fetched = rate.fetched)
    }

    companion object {
        const val ARG_ACCOUNT_ID = "accountId"

        /** The default "Real estate" account type, whose accounts can become houses. */
        const val REAL_ESTATE_SEED_KEY = "real_estate"

        val Factory = appViewModelFactoryWithState { container, handle ->
            HouseEditViewModel(
                handle, container.houseRepository, container.accountRepository, container.catalogRepository,
                container.currencyRepository, container.rateUpdater,
            )
        }
    }
}
