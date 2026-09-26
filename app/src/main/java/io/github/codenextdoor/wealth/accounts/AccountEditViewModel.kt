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
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import io.github.codenextdoor.wealth.ui.appViewModelFactoryWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What the user has typed/selected so far. Text fields stay as text until saved. */
data class AccountForm(
    val name: String = "",
    val typeId: Long? = null,
    val currencyCode: String? = null,
    val countryId: Long? = null,
    val balanceText: String = "",
    val institution: String = "",
    val note: String = "",
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
}

class AccountEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** Null when adding a new account. */
    private val accountId: Long? = savedStateHandle.get<Long>(ARG_ACCOUNT_ID)?.takeIf { it > 0 }

    private val form = MutableStateFlow(AccountForm())
    private val status = MutableStateFlow(Status(isReady = accountId == null, isFinished = false))

    private data class Status(val isReady: Boolean, val isFinished: Boolean)

    val uiState: StateFlow<AccountEditUiState> = combine(
        form,
        status,
        catalogRepository.accountTypes,
        catalogRepository.countries,
        currencyRepository.currencies,
    ) { form, status, types, countries, currencies ->
        AccountEditUiState(
            isNew = accountId == null,
            isReady = status.isReady,
            form = form,
            types = types,
            countries = countries,
            currencies = currencies,
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
                status.update { it.copy(isReady = true) }
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

    fun onBalanceChange(value: String) = form.update { it.copy(balanceText = value) }

    fun onInstitutionChange(value: String) = form.update { it.copy(institution = value) }

    fun onNoteChange(value: String) = form.update { it.copy(note = value) }

    fun save() {
        form.update { it.copy(showErrors = true) }
        val state = uiState.value.copy(form = form.value)
        val type = state.selectedType
        val currency = state.selectedCurrency
        val balance = state.balanceMinor
        if (state.form.name.isBlank() || type == null || currency == null || balance == null) return

        viewModelScope.launch {
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
