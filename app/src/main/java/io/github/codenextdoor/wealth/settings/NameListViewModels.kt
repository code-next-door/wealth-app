package io.github.codenextdoor.wealth.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared logic for screens that edit a plain list of names. */
abstract class NameListViewModel(source: Flow<List<NamedItem>>) : ViewModel() {

    val items: StateFlow<List<NamedItem>> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    protected abstract suspend fun insert(name: String)
    protected abstract suspend fun update(id: Long, name: String)
    protected abstract suspend fun remove(id: Long)

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { insert(trimmed) }
    }

    fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { update(id, trimmed) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { remove(id) }
    }
}

class CategoriesViewModel(private val repository: CatalogRepository) : NameListViewModel(
    repository.expenseCategories.mapItems { NamedItem(it.id, it.name) },
) {
    override suspend fun insert(name: String) = repository.addExpenseCategory(name)
    override suspend fun update(id: Long, name: String) = repository.renameExpenseCategory(id, name)
    override suspend fun remove(id: Long) = repository.deleteExpenseCategory(id)

    companion object {
        val Factory = appViewModelFactory { CategoriesViewModel(it.catalogRepository) }
    }
}

class CountriesViewModel(private val repository: CatalogRepository) : NameListViewModel(
    repository.countries.mapItems { NamedItem(it.id, it.name) },
) {
    override suspend fun insert(name: String) = repository.addCountry(name)
    override suspend fun update(id: Long, name: String) = repository.renameCountry(id, name)
    override suspend fun remove(id: Long) = repository.deleteCountry(id)

    companion object {
        val Factory = appViewModelFactory { CountriesViewModel(it.catalogRepository) }
    }
}

private fun <T, R> Flow<List<T>>.mapItems(transform: (T) -> R): Flow<List<R>> =
    map { list -> list.map(transform) }

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    NameListScreen(
        title = stringResource(R.string.settings_categories_title),
        addLabel = stringResource(R.string.category_add),
        deleteMessage = stringResource(R.string.delete_message_generic),
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@Composable
fun CountriesRoute(
    onBack: () -> Unit,
    viewModel: CountriesViewModel = viewModel(factory = CountriesViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    NameListScreen(
        title = stringResource(R.string.settings_countries_title),
        addLabel = stringResource(R.string.country_add),
        deleteMessage = stringResource(R.string.country_delete_message),
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}
