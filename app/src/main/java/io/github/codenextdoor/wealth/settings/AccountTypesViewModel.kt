package io.github.codenextdoor.wealth.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How a group of account types is labelled on screen. */
sealed interface AccountTypeGroup {
    data class InCountry(val countryName: String) : AccountTypeGroup
    data object General : AccountTypeGroup
    data object Liabilities : AccountTypeGroup
}

data class AccountTypeSection(val group: AccountTypeGroup, val types: List<AccountType>)

data class AccountTypesUiState(
    val sections: List<AccountTypeSection> = emptyList(),
    val countries: List<Country> = emptyList(),
)

class AccountTypesViewModel(private val repository: CatalogRepository) : ViewModel() {

    val uiState: StateFlow<AccountTypesUiState> = combine(
        repository.accountTypes,
        repository.countries,
    ) { types, countries ->
        val (assets, liabilities) = types.partition { it.kind == AssetKind.ASSET }
        val countryIds = countries.map { it.id }.toSet()
        val sections = buildList {
            countries.forEach { country ->
                add(AccountTypeSection(AccountTypeGroup.InCountry(country.name), assets.filter { it.countryId == country.id }))
            }
            add(AccountTypeSection(AccountTypeGroup.General, assets.filter { it.countryId !in countryIds }))
            add(AccountTypeSection(AccountTypeGroup.Liabilities, liabilities))
        }.filter { it.types.isNotEmpty() }
        AccountTypesUiState(sections, countries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountTypesUiState())

    /** Creates a new type when [id] is null, otherwise updates it. */
    fun save(id: Long?, name: String, kind: AssetKind, countryId: Long?, holdsShares: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (id == null) {
                repository.addAccountType(trimmed, kind, countryId, holdsShares)
            } else {
                repository.updateAccountType(id, trimmed, kind, countryId, holdsShares)
            }
        }
    }

    private val _deleteBlocked = MutableStateFlow<String?>(null)

    /** Name of a type that couldn't be deleted because accounts use it. */
    val deleteBlocked: StateFlow<String?> = _deleteBlocked.asStateFlow()

    fun delete(id: Long) {
        val name = uiState.value.sections.flatMap { it.types }.firstOrNull { it.id == id }?.name
        viewModelScope.launch {
            if (!repository.deleteAccountType(id)) _deleteBlocked.value = name.orEmpty()
        }
    }

    fun dismissDeleteBlocked() {
        _deleteBlocked.value = null
    }

    companion object {
        val Factory = appViewModelFactory { AccountTypesViewModel(it.catalogRepository) }
    }
}
