package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.RatePoint
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.codenextdoor.wealth.ui.FormState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.PricePoint
import io.github.codenextdoor.wealth.domain.parseNonNegativeDecimal
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanBalance
import io.github.codenextdoor.wealth.domain.LoanRateChange
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.data.repository.LoanRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The form's text fields. Compose's TextFieldState owns what's typed, so fast
 * typing never goes out of sync; the ViewModel reads it when needed.
 */
class AccountTextFields {
    val name = TextFieldState()
    val balance = TextFieldState()
    val institution = TextFieldState()
    val note = TextFieldState()
    val rate = TextFieldState()

    // Accounts holding shares.
    val symbol = TextFieldState()
    val units = TextFieldState()
    val price = TextFieldState()

    // Calculated loans.
    val loanPrincipal = TextFieldState()
    val loanEmi = TextFieldState()
    val loanRate = TextFieldState()
}

/** A snapshot of the form: text read from [AccountTextFields] plus the choices made. */
data class AccountForm(
    val name: String = "",
    val typeId: Long? = null,
    val currencyCode: String? = null,
    val countryId: Long? = null,
    val balanceText: String = "",
    /** The day [balanceText] applies to. Defaults to today. */
    val balanceDate: LocalDate = LocalDate.now(),
    /** Exchange rate typed by the user; null follows the known rate for [balanceDate]. */
    val rateText: String? = null,
    /** For accounts holding shares: ticker, number of shares, and a typed price (null follows the known one). */
    val symbol: String = "",
    val unitsText: String = "",
    val priceText: String? = null,
    val institution: String = "",
    val note: String = "",
    /** Counted in net worth (the default); off keeps the account listed but leaves it out. */
    val inNetWorth: Boolean = true,
    /** True once the user types in the balance field; until then it follows the latest history entry. */
    val balanceEditedByUser: Boolean = false,
    /** Once the user picks a country, choosing a type no longer overwrites it. */
    val countryChosenByUser: Boolean = false,
    /** For loan types: the outstanding is calculated from the terms below (see LoanBalance). */
    val calculateLoan: Boolean = false,
    val loanPrincipalText: String = "",
    val loanFirstEmi: LocalDate = LocalDate.now(),
    val loanEmiText: String = "",
    val loanRateText: String = "",
    /** The category the loan's EMIs are imported into (their interest counts as spending). */
    val loanEmiCategoryId: Long? = null,
    val loanRateChanges: List<LoanRateChange> = emptyList(),
    val showErrors: Boolean = false,
)

data class AccountEditUiState(
    val isNew: Boolean = true,
    /** False until an existing account has been loaded into the form. */
    val isReady: Boolean = false,
    val form: AccountForm = AccountForm(),
    val types: List<AccountType> = emptyList(),
    val countries: List<Country> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    /** Saved balances for an existing account, newest first. */
    val history: List<BalanceEntry> = emptyList(),
    val baseCurrency: String = "",
    val rateBook: RateBook = RateBook(emptyList()),
    /** Downloading the rate for the main balance's currency and date. */
    val rateStatus: RateStatus = RateStatus.Idle,
    val priceBook: PriceBook = PriceBook.EMPTY,
    /** Downloading the share price for the balance's date. */
    val priceStatus: RateStatus = RateStatus.Idle,
    /** Set after a successful save or delete, so the screen can close. */
    val isFinished: Boolean = false,
    /** Spending categories, for the loan's EMI category. */
    val categories: List<ExpenseCategory> = emptyList(),
) {
    val selectedType: AccountType? get() = types.firstOrNull { it.id == form.typeId }
    val selectedCurrency: Currency? get() = currencies.firstOrNull { it.code == form.currencyCode }

    /** The account holds shares: its balance is the cash beside them, and may be left empty. */
    val holdsShares: Boolean get() = selectedType?.holdsShares == true
    val balanceMinor: Long?
        get() = selectedCurrency?.let {
            if (holdsShares && form.balanceText.isBlank()) 0L else parseAmountToMinor(form.balanceText, it.decimals)
        }
    val shareSymbol: String get() = form.symbol.trim().uppercase()
    val units: BigDecimal? get() = form.unitsText.trim().takeIf { it.isNotEmpty() }?.let { parseNonNegativeDecimal(it) }
    val symbolError get() = form.showErrors && holdsShares && shareSymbol.isEmpty()
    val unitsError get() = form.showErrors && holdsShares && units == null

    /** Share price field; null unless the account holds shares and has a symbol. */
    val priceModel: PriceFieldModel?
        get() = shareSymbol.takeIf { holdsShares && it.isNotEmpty() }?.let {
            PriceFieldModel(it, form.currencyCode.orEmpty(), priceBook.priceAt(it, form.balanceDate), (priceStatus as? RateStatus.Found)?.rate)
        }
    val priceText: String get() = form.priceText ?: priceModel?.defaultText.orEmpty()
    val priceEntry: Result<PriceEntry?> get() = priceModel?.entryFor(priceText, form.priceText != null) ?: Result.success(null)
    val priceError get() = form.showErrors && priceEntry.isFailure
    val savedPrice: PricePoint? get() = shareSymbol.takeIf { holdsShares && it.isNotEmpty() }?.let { priceBook.pointAt(it, form.balanceDate) }

    /** A loan type: the form can calculate its outstanding. */
    val isLoanType: Boolean get() = selectedType?.isLoan == true
    val calculatingLoan: Boolean get() = isLoanType && form.calculateLoan
    val loanPrincipalMinor: Long? get() = selectedCurrency?.let { parseAmountToMinor(form.loanPrincipalText, it.decimals) }?.takeIf { it > 0 }
    val loanEmiMinor: Long? get() = selectedCurrency?.let { parseAmountToMinor(form.loanEmiText, it.decimals) }?.takeIf { it > 0 }
    val loanRate: BigDecimal? get() = form.loanRateText.trim().takeIf { it.isNotEmpty() }?.let { parseNonNegativeDecimal(it) }
    val loanPrincipalError get() = form.showErrors && calculatingLoan && loanPrincipalMinor == null
    val loanEmiError get() = form.showErrors && calculatingLoan && loanEmiMinor == null
    val loanRateError get() = form.showErrors && calculatingLoan && loanRate == null

    /** The loan as the form describes it; null while something's missing. */
    fun loan(accountId: Long): Loan? {
        val principal = loanPrincipalMinor ?: return null
        val emi = loanEmiMinor ?: return null
        val rate = loanRate ?: return null
        return Loan(accountId, principal, form.loanFirstEmi, emi, rate, form.loanEmiCategoryId, form.loanRateChanges)
    }

    /** The known balances as the form describes them: the account's, with the principal before the first EMI. */
    val loanKnown: List<Pair<LocalDate, Long>>
        get() {
            val principal = loanPrincipalMinor ?: return emptyList()
            val anchor = form.loanFirstEmi.minusDays(1) to principal
            return history.filter { it.date != anchor.first }.map { it.date to it.balanceMinor } + anchor
        }

    /** The outstanding on [date], as calculated from the form; null while terms are missing. */
    fun loanOutstandingOn(date: LocalDate): Long? = loan(0)?.let { LoanBalance.at(it, loanKnown, date) }

    val loanOutstandingToday: Long? get() = loanOutstandingOn(LocalDate.now())

    val nameError get() = form.showErrors && form.name.isBlank()
    val typeError get() = form.showErrors && selectedType == null
    val currencyError get() = form.showErrors && selectedCurrency == null
    val balanceError get() = form.showErrors && !calculatingLoan && selectedCurrency != null && balanceMinor == null

    /** Units of base currency per 1 unit of [currency] known for [date]. */
    fun rateOn(currency: String, date: LocalDate): java.math.BigDecimal? =
        rateBook.converterAt(date).rate(currency, baseCurrency)

    /** Rate field for the main balance; null when the account is in the base currency. */
    val rateModel: RateFieldModel?
        get() = selectedCurrency?.code?.takeIf { it != baseCurrency && baseCurrency.isNotEmpty() }?.let {
            RateFieldModel(it, baseCurrency, rateOn(it, form.balanceDate), (rateStatus as? RateStatus.Found)?.rate)
        }

    /** The saved rate in effect for the main balance's date, for the field's hint. */
    val savedRate: RatePoint?
        get() = selectedCurrency?.code?.let { rateBook.pointAt(it, baseCurrency, form.balanceDate) }
    val rateText: String get() = form.rateText ?: rateModel?.defaultText.orEmpty()
    val rateEntry: Result<RateEntry?> get() = rateModel?.entryFor(rateText, form.rateText != null) ?: Result.success(null)
    val rateError get() = form.showErrors && rateEntry.isFailure
}

class AccountEditViewModel(
    /** Null adds a new one. */
    private val accountId: Long?,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
    rateUpdater: RateUpdater,
    private val shareRepository: ShareRepository,
    priceUpdater: PriceUpdater,
    /** Added from the Assets or Liabilities section: only that kind's types are offered. */
    private val kind: AssetKind? = null,
    /** Calculated loans (null in tests that don't use them). */
    private val loanRepository: LoanRepository? = null,
) : ViewModel() {

    /** Downloads the rate for each currency and date the form shows. */
    val rateLookups = RateLookups(rateUpdater, viewModelScope)

    /** Downloads share prices for accounts holding shares. */
    val priceLookups = RateLookups.forPrices(priceUpdater, viewModelScope)

    /** Null when adding a new account. */

    val fields = AccountTextFields()

    /** Choices other than text (type, currency, country, date, flags); the text parts are unused here. */
    private val form = FormState(AccountForm())

    /** Balance text as last filled in by the app; anything else was typed by the user. */
    private var filledBalanceText by mutableStateOf("")

    /** The saved rate currently shown in the rate field; anything else was typed by the user. */
    private var shownRateDefault by mutableStateOf("")

    /** Same for the share price field. */
    private var shownPriceDefault by mutableStateOf("")

    /** Shares when the form was opened, to tell whether the user changed them. */
    private var originalUnits: BigDecimal? = null
    /** Ready once the account (or, for a new one, its default currency) is loaded. */
    private val status = MutableStateFlow(Status(isReady = false, isFinished = false))

    internal data class Status(val isReady: Boolean, val isFinished: Boolean)

    /** Balance when the form was opened, to tell whether the user changed it. */
    private var originalBalanceMinor: Long? = null

    /** The account was a calculated loan when the form opened. */
    private var wasLoan = false

    internal data class Lists(
        val types: List<AccountType>,
        val countries: List<Country>,
        val currencies: List<Currency>,
        val history: List<BalanceEntry>,
        val rates: Pair<String, RateBook>,
        val prices: PriceBook,
        val categories: List<ExpenseCategory> = emptyList(),
    )

    private val lists = combine(
        catalogRepository.accountTypes,
        catalogRepository.countries,
        currencyRepository.currencies,
        if (accountId == null) flowOf(emptyList()) else accountRepository.observeHistory(accountId),
        combine(currencyRepository.baseCurrency, currencyRepository.rateBook, shareRepository.prices, catalogRepository.expenseCategories) { base, book, prices, categories ->
            Money(base, book, prices, categories)
        },
    ) { types, countries, currencies, history, money ->
        Lists(types, countries, currencies, history, money.base to money.book, money.prices, money.categories.filterNot { it.isIncome })
    }

    private data class Money(val base: String, val book: RateBook, val prices: PriceBook, val categories: List<ExpenseCategory>)

    /** Database-backed parts of the screen. The form itself lives in [form]. */
    class Data internal constructor(internal val status: Status, internal val lists: Lists?)

    val data: StateFlow<Data> = combine(status, lists) { status, lists -> Data(status, lists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(status.value, null))

    /** The full screen state. Reads [form] (Compose state), so composables recompose as the user types. */
    fun uiState(data: Data = this.data.value): AccountEditUiState {
        val status = data.status
        val lists = data.lists
        val balanceText = fields.balance.text.toString()
        val rateText = fields.rate.text.toString()
        val choices = form.value
        val base = lists?.rates?.first.orEmpty()
        val rateStatus = choices.currencyCode?.takeIf { it != base }?.let { rateLookups.statusFor(it, choices.balanceDate) } ?: RateStatus.Idle
        val symbol = fields.symbol.text.toString().trim().uppercase()
        val priceStatus = symbol.takeIf { it.isNotEmpty() }?.let { priceLookups.statusFor(it, choices.balanceDate) } ?: RateStatus.Idle
        val priceText = fields.price.text.toString()
        return AccountEditUiState(
            isNew = accountId == null,
            isReady = status.isReady && lists != null,
            form = choices.copy(
                name = fields.name.text.toString(),
                balanceText = balanceText,
                institution = fields.institution.text.toString(),
                note = fields.note.text.toString(),
                rateText = rateText.takeIf { it != shownRateDefault },
                symbol = fields.symbol.text.toString(),
                unitsText = fields.units.text.toString(),
                loanPrincipalText = fields.loanPrincipal.text.toString(),
                loanEmiText = fields.loanEmi.text.toString(),
                loanRateText = fields.loanRate.text.toString(),
                priceText = priceText.takeIf { it != shownPriceDefault },
                balanceEditedByUser = balanceText != filledBalanceText,
            ),
            // From a section: its kind; editing: the account's own kind; otherwise all types.
            types = lists?.types.orEmpty().let { all ->
                val shownKind = kind ?: all.firstOrNull { it.id == choices.typeId }?.takeIf { accountId != null }?.kind
                if (shownKind == null) all else all.filter { it.kind == shownKind }
            },
            countries = lists?.countries.orEmpty(),
            currencies = lists?.currencies.orEmpty(),
            history = lists?.history.orEmpty(),
            baseCurrency = lists?.rates?.first.orEmpty(),
            rateBook = lists?.rates?.second ?: RateBook(emptyList()),
            rateStatus = rateStatus,
            priceBook = lists?.prices ?: PriceBook.EMPTY,
            priceStatus = priceStatus,
            isFinished = status.isFinished,
            categories = lists?.categories.orEmpty(),
        )
    }

    init {
        viewModelScope.launch {
            if (accountId == null) {
                // New accounts start in the base currency; the most common case.
                val base = currencyRepository.baseCurrency.first()
                form.update { if (it.currencyCode == null) it.copy(currencyCode = base) else it }
                status.update { it.copy(isReady = true) }
            } else {
                val account = accountRepository.get(accountId)
                if (account == null) {
                    status.update { it.copy(isFinished = true) }
                    return@launch
                }
                val decimals = currencyRepository.currencies.first()
                    .firstOrNull { it.code == account.currencyCode }?.decimals ?: 2
                form.value = AccountForm(
                    typeId = account.accountTypeId,
                    currencyCode = account.currencyCode,
                    countryId = account.countryId,
                    inNetWorth = !account.excludedFromNetWorth,
                    countryChosenByUser = true,
                )
                fields.name.setTextAndPlaceCursorAtEnd(account.name)
                fillBalance(minorToInputText(account.balanceMinor, decimals))
                fields.institution.setTextAndPlaceCursorAtEnd(account.institution.orEmpty())
                fields.note.setTextAndPlaceCursorAtEnd(account.note.orEmpty())
                fields.symbol.setTextAndPlaceCursorAtEnd(account.shareSymbol.orEmpty())
                fields.units.setTextAndPlaceCursorAtEnd(account.units?.stripTrailingZeros()?.toPlainString().orEmpty())
                originalBalanceMinor = account.balanceMinor
                originalUnits = account.units
                loanRepository?.forAccount(accountId)?.let { loan ->
                    fields.loanPrincipal.setTextAndPlaceCursorAtEnd(minorToInputText(loan.principalMinor, decimals))
                    fields.loanEmi.setTextAndPlaceCursorAtEnd(minorToInputText(loan.emiMinor, decimals))
                    fields.loanRate.setTextAndPlaceCursorAtEnd(loan.yearlyRate.stripTrailingZeros().toPlainString())
                    form.update {
                        it.copy(
                            calculateLoan = true,
                            loanFirstEmi = loan.firstEmiDate,
                            loanEmiCategoryId = loan.emiCategoryId,
                            loanRateChanges = loan.rateChanges,
                        )
                    }
                    wasLoan = true
                }
                status.update { it.copy(isReady = true) }

                // History edits can change the current balance; show it unless the user is typing one.
                accountRepository.observeHistory(accountId).collect { history ->
                    val latest = history.firstOrNull() ?: return@collect
                    if (!uiState().form.balanceEditedByUser && latest.balanceMinor != originalBalanceMinor) {
                        originalBalanceMinor = latest.balanceMinor
                        fillBalance(minorToInputText(latest.balanceMinor, decimals))
                    }
                }
            }
        }
    }

    private fun fillBalance(text: String) {
        fields.balance.setTextAndPlaceCursorAtEnd(text)
        filledBalanceText = text
    }

    /**
     * Called with the saved rate for the balance's date whenever it changes;
     * the rate field follows it until the user types their own.
     */
    fun showRateDefault(text: String) {
        if (fields.rate.text.toString() == shownRateDefault) fields.rate.setTextAndPlaceCursorAtEnd(text)
        shownRateDefault = text
    }

    /** Like [showRateDefault], for the share price field. */
    fun showPriceDefault(text: String) {
        if (fields.price.text.toString() == shownPriceDefault) fields.price.setTextAndPlaceCursorAtEnd(text)
        shownPriceDefault = text
    }

    // Programmatic edits (the screen edits the text fields directly).
    fun onNameChange(value: String) = fields.name.setTextAndPlaceCursorAtEnd(value)

    fun onTypeChange(typeId: Long) {
        val type = uiState().types.firstOrNull { it.id == typeId }
        form.update {
            // Default the country to the type's, e.g. "NRE account" -> India.
            if (it.countryChosenByUser) it.copy(typeId = typeId) else it.copy(typeId = typeId, countryId = type?.countryId)
        }
    }

    fun onCurrencyChange(code: String) = form.update { it.copy(currencyCode = code) }

    fun onCountryChange(countryId: Long?) =
        form.update { it.copy(countryId = countryId, countryChosenByUser = true) }

    fun onBalanceChange(value: String) = fields.balance.setTextAndPlaceCursorAtEnd(value)

    fun onBalanceDateChange(date: LocalDate) = form.update { it.copy(balanceDate = date) }

    fun onInNetWorthChange(counted: Boolean) = form.update { it.copy(inNetWorth = counted) }

    fun onRateChange(value: String) = fields.rate.setTextAndPlaceCursorAtEnd(value)

    fun editHistoryEntry(entryId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal? = null, price: PriceEntry? = null) {
        viewModelScope.launch {
            saveRate(rate, date)
            savePrice(price, date)
            accountRepository.updateHistoryEntry(entryId, date, balanceMinor, units)
        }
    }

    fun addHistoryEntry(date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal? = null, price: PriceEntry? = null) {
        val id = accountId ?: return
        viewModelScope.launch {
            saveRate(rate, date)
            savePrice(price, date)
            accountRepository.addHistoryEntry(id, date, balanceMinor, units)
        }
    }

    private suspend fun saveRate(rate: RateEntry?, date: LocalDate) {
        if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date, fetched = rate.fetched)
    }

    private suspend fun savePrice(price: PriceEntry?, date: LocalDate) {
        if (price != null) shareRepository.setPrice(price.symbol, date, price.price, fetched = price.fetched)
    }

    fun deleteHistoryEntry(entryId: Long) {
        viewModelScope.launch { accountRepository.deleteHistoryEntry(entryId) }
    }

    fun onCalculateLoanChange(on: Boolean) = form.update { it.copy(calculateLoan = on) }
    fun onLoanFirstEmiChange(date: LocalDate) = form.update { it.copy(loanFirstEmi = date) }
    fun onLoanEmiCategoryChange(categoryId: Long?) = form.update { it.copy(loanEmiCategoryId = categoryId) }

    /** Adds a rate change; [emiText] blank keeps the EMI. Returns false (and adds nothing) if the rate or EMI isn't valid. */
    fun addRateChange(from: LocalDate, rateText: String, emiText: String): Boolean {
        val rate = parseNonNegativeDecimal(rateText.trim()) ?: return false
        val decimals = uiState().selectedCurrency?.decimals ?: 2
        val emi = if (emiText.isBlank()) null else parseAmountToMinor(emiText, decimals)?.takeIf { it > 0 } ?: return false
        form.update { f -> f.copy(loanRateChanges = (f.loanRateChanges.filterNot { it.from == from } + LoanRateChange(0, from, rate, emi)).sortedBy { it.from }) }
        return true
    }

    fun removeRateChange(from: LocalDate) = form.update { f -> f.copy(loanRateChanges = f.loanRateChanges.filterNot { it.from == from }) }

    /** Replaces the rate change from [oldFrom]; false (and no change) if the rate or EMI isn't valid. */
    fun editRateChange(oldFrom: LocalDate, from: LocalDate, rateText: String, emiText: String): Boolean {
        val before = form.value.loanRateChanges
        removeRateChange(oldFrom)
        if (addRateChange(from, rateText, emiText)) return true
        form.update { it.copy(loanRateChanges = before) }
        return false
    }

    fun onInstitutionChange(value: String) = fields.institution.setTextAndPlaceCursorAtEnd(value)

    fun onNoteChange(value: String) = fields.note.setTextAndPlaceCursorAtEnd(value)

    fun save() {
        form.update { it.copy(showErrors = true) }
        val state = uiState()
        val type = state.selectedType
        val currency = state.selectedCurrency
        val calculating = state.calculatingLoan
        if (calculating && state.loan(0) == null) return
        // A calculated loan's known balance is its principal, the day before the first EMI.
        val balance = if (calculating) state.loanPrincipalMinor else state.balanceMinor
        val balanceDate = if (calculating) state.form.loanFirstEmi.minusDays(1) else state.form.balanceDate
        val rate = if (calculating) Result.success(null) else state.rateEntry
        val price = state.priceEntry
        if (state.form.name.isBlank() || type == null || currency == null || balance == null || rate.isFailure) return
        if (state.holdsShares && (state.shareSymbol.isEmpty() || state.units == null || price.isFailure)) return
        val units = if (state.holdsShares) state.units else null

        // Record a history entry only for a real change; editing the name alone shouldn't.
        val recordBalance = accountId == null || calculating ||
            balance != originalBalanceMinor ||
            units?.compareTo(originalUnits ?: BigDecimal.ZERO)?.let { it != 0 } == true ||
            state.form.balanceDate != LocalDate.now()

        viewModelScope.launch {
            saveRate(rate.getOrNull(), state.form.balanceDate)
            savePrice(price.getOrNull(), state.form.balanceDate)
            val savedId = accountRepository.save(
                Account(
                    id = accountId ?: 0,
                    name = state.form.name.trim(),
                    accountTypeId = type.id,
                    currencyCode = currency.code,
                    countryId = state.form.countryId,
                    balanceMinor = balance,
                    balanceUpdatedAt = Instant.now(), // The repository decides the real value.
                    institution = state.form.institution.trim().ifEmpty { null },
                    note = state.form.note.trim().ifEmpty { null },
                    shareSymbol = state.shareSymbol.takeIf { state.holdsShares },
                    units = units,
                    excludedFromNetWorth = !state.form.inNetWorth,
                ),
                balanceDate = balanceDate,
                recordBalance = recordBalance,
            )
            when {
                calculating -> loanRepository?.save(state.loan(savedId)!!)
                wasLoan -> loanRepository?.delete(savedId) // switched off: a plain liability again
            }
            status.update { it.copy(isFinished = true) }
        }
    }

    fun delete() {
        val id = accountId ?: return
        viewModelScope.launch {
            accountRepository.delete(id)
            status.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        fun factory(accountId: Long?, kind: AssetKind? = null) = appViewModelFactory { container ->
            AccountEditViewModel(
                accountId,
                container.accountRepository,
                container.catalogRepository,
                container.currencyRepository,
                container.rateUpdater,
                container.shareRepository,
                container.priceUpdater,
                kind,
                container.loanRepository,
            )
        }
    }
}

/** Liabilities are entered as the amount owed, so the field label changes. */
val AccountEditUiState.isLiability: Boolean get() = selectedType?.kind == AssetKind.LIABILITY
