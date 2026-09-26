package io.github.codenextdoor.wealth.accounts

import androidx.lifecycle.SavedStateHandle
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
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
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
import java.time.Instant
import java.time.LocalDate

/** What the user has typed/selected so far. Text fields stay as text until saved. */
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
    val institution: String = "",
    val note: String = "",
    /** True once the user types in the balance field; until then it follows the latest history entry. */
    val balanceEditedByUser: Boolean = false,
    /** Once the user picks a country, choosing a type no longer overwrites it. */
    val countryChosenByUser: Boolean = false,
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
    /** Set after a successful save or delete, so the screen can close. */
    val isFinished: Boolean = false,
) {
    val selectedType: AccountType? get() = types.firstOrNull { it.id == form.typeId }
    val selectedCurrency: Currency? get() = currencies.firstOrNull { it.code == form.currencyCode }
    val balanceMinor: Long? get() = selectedCurrency?.let { parseAmountToMinor(form.balanceText, it.decimals) }

    val nameError get() = form.showErrors && form.name.isBlank()
    val typeError get() = form.showErrors && selectedType == null
    val currencyError get() = form.showErrors && selectedCurrency == null
    val balanceError get() = form.showErrors && selectedCurrency != null && balanceMinor == null

    /** Units of base currency per 1 unit of [currency] known for [date]. */
    fun rateOn(currency: String, date: LocalDate): java.math.BigDecimal? =
        rateBook.converterAt(date).rate(currency, baseCurrency)

    /** Rate field for the main balance; null when the account is in the base currency. */
    val rateModel: RateFieldModel?
        get() = selectedCurrency?.code?.takeIf { it != baseCurrency && baseCurrency.isNotEmpty() }?.let {
            RateFieldModel(it, baseCurrency, rateOn(it, form.balanceDate))
        }
    val rateText: String get() = form.rateText ?: rateModel?.defaultText.orEmpty()
    val rateEntry: Result<RateEntry?> get() = rateModel?.entryFor(rateText, form.rateText != null) ?: Result.success(null)
    val rateError get() = form.showErrors && rateEntry.isFailure
}

class AccountEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** Null when adding a new account. */
    private val accountId: Long? = savedStateHandle.get<Long>(ARG_ACCOUNT_ID)?.takeIf { it > 0 }

    private val form = MutableStateFlow(AccountForm())
    private val status = MutableStateFlow(Status(isReady = accountId == null, isFinished = false))

    private data class Status(val isReady: Boolean, val isFinished: Boolean)

    /** Balance when the form was opened, to tell whether the user changed it. */
    private var originalBalanceMinor: Long? = null

    private data class Lists(
        val types: List<AccountType>,
        val countries: List<Country>,
        val currencies: List<Currency>,
        val history: List<BalanceEntry>,
        val rates: Pair<String, RateBook>,
    )

    private val lists = combine(
        catalogRepository.accountTypes,
        catalogRepository.countries,
        currencyRepository.currencies,
        if (accountId == null) flowOf(emptyList()) else accountRepository.observeHistory(accountId),
        combine(currencyRepository.baseCurrency, currencyRepository.rateBook) { base, book -> base to book },
        ::Lists,
    )

    val uiState: StateFlow<AccountEditUiState> = combine(form, status, lists) { form, status, lists ->
        AccountEditUiState(
            isNew = accountId == null,
            isReady = status.isReady,
            form = form,
            types = lists.types,
            countries = lists.countries,
            currencies = lists.currencies,
            history = lists.history,
            baseCurrency = lists.rates.first,
            rateBook = lists.rates.second,
            isFinished = status.isFinished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountEditUiState(isNew = accountId == null))

    init {
        viewModelScope.launch {
            if (accountId == null) {
                // New accounts start in the base currency; the most common case.
                val base = currencyRepository.baseCurrency.first()
                form.update { if (it.currencyCode == null) it.copy(currencyCode = base) else it }
            } else {
                val account = accountRepository.get(accountId)
                if (account == null) {
                    status.update { it.copy(isFinished = true) }
                    return@launch
                }
                val decimals = currencyRepository.currencies.first()
                    .firstOrNull { it.code == account.currencyCode }?.decimals ?: 2
                form.value = AccountForm(
                    name = account.name,
                    typeId = account.accountTypeId,
                    currencyCode = account.currencyCode,
                    countryId = account.countryId,
                    balanceText = minorToInputText(account.balanceMinor, decimals),
                    institution = account.institution.orEmpty(),
                    note = account.note.orEmpty(),
                    countryChosenByUser = true,
                )
                originalBalanceMinor = account.balanceMinor
                status.update { it.copy(isReady = true) }

                // History edits can change the current balance; show it unless the user is typing one.
                accountRepository.observeHistory(accountId).collect { history ->
                    val latest = history.firstOrNull() ?: return@collect
                    if (!form.value.balanceEditedByUser && latest.balanceMinor != originalBalanceMinor) {
                        originalBalanceMinor = latest.balanceMinor
                        form.update { it.copy(balanceText = minorToInputText(latest.balanceMinor, decimals)) }
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = form.update { it.copy(name = value) }

    fun onTypeChange(typeId: Long) {
        val type = uiState.value.types.firstOrNull { it.id == typeId }
        form.update {
            // Default the country to the type's, e.g. "NRE account" -> India.
            if (it.countryChosenByUser) it.copy(typeId = typeId) else it.copy(typeId = typeId, countryId = type?.countryId)
        }
    }

    fun onCurrencyChange(code: String) = form.update { it.copy(currencyCode = code) }

    fun onCountryChange(countryId: Long?) =
        form.update { it.copy(countryId = countryId, countryChosenByUser = true) }

    fun onBalanceChange(value: String) = form.update { it.copy(balanceText = value, balanceEditedByUser = true) }

    fun onBalanceDateChange(date: LocalDate) = form.update { it.copy(balanceDate = date) }

    fun onRateChange(value: String) = form.update { it.copy(rateText = value) }

    fun editHistoryEntry(entryId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?) {
        viewModelScope.launch {
            saveRate(rate, date)
            accountRepository.updateHistoryEntry(entryId, date, balanceMinor)
        }
    }

    fun addHistoryEntry(date: LocalDate, balanceMinor: Long, rate: RateEntry?) {
        val id = accountId ?: return
        viewModelScope.launch {
            saveRate(rate, date)
            accountRepository.addHistoryEntry(id, date, balanceMinor)
        }
    }

    private suspend fun saveRate(rate: RateEntry?, date: LocalDate) {
        if (rate != null) currencyRepository.setRate(rate.from, rate.to, rate.rate, date)
    }

    fun deleteHistoryEntry(entryId: Long) {
        viewModelScope.launch { accountRepository.deleteHistoryEntry(entryId) }
    }

    fun onInstitutionChange(value: String) = form.update { it.copy(institution = value) }

    fun onNoteChange(value: String) = form.update { it.copy(note = value) }

    fun save() {
        form.update { it.copy(showErrors = true) }
        val state = uiState.value.copy(form = form.value)
        val type = state.selectedType
        val currency = state.selectedCurrency
        val balance = state.balanceMinor
        val rate = state.rateEntry
        if (state.form.name.isBlank() || type == null || currency == null || balance == null || rate.isFailure) return

        // Record a history entry only for a real change; editing the name alone shouldn't.
        val recordBalance = accountId == null ||
            balance != originalBalanceMinor ||
            state.form.balanceDate != LocalDate.now()

        viewModelScope.launch {
            saveRate(rate.getOrNull(), state.form.balanceDate)
            accountRepository.save(
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
                ),
                balanceDate = state.form.balanceDate,
                recordBalance = recordBalance,
            )
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
        const val ARG_ACCOUNT_ID = "accountId"

        val Factory = appViewModelFactoryWithState { container, handle ->
            AccountEditViewModel(
                handle,
                container.accountRepository,
                container.catalogRepository,
                container.currencyRepository,
            )
        }
    }
}

/** Liabilities are entered as the amount owed, so the field label changes. */
val AccountEditUiState.isLiability: Boolean get() = selectedType?.kind == AssetKind.LIABILITY
