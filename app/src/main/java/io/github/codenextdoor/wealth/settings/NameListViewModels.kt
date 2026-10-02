package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import androidx.compose.foundation.text.input.TextFieldState
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/** What the "test a statement line" box found: the pattern and its category. */
data class TestMatch(val keyword: String, val categoryId: Long, val categoryName: String, val countsAsSpending: Boolean)

/**
 * Settings › Categories and patterns: the categories in their sections (switch, order), how
 * many patterns each has, and a box to test which category a statement line would get. A
 * category opens into its patterns ([CategoryDetailViewModel]).
 */
class CategoriesViewModel(
    private val repository: CatalogRepository,
    private val expenses: ExpenseRepository,
) : NameListViewModel(
    // Two sections: spending (0, with the "counts as spending" switch) and income (1, no switch).
    repository.expenseCategories.mapItems {
        NamedItem(it.id, it.name, checked = if (it.isIncome) null else it.countsAsSpending, group = if (it.isIncome) INCOME else SPENDING)
    },
) {
    /** The patterns and categories, for the test box. */
    data class Lists(val rules: List<CategoryRule> = emptyList(), val categories: List<ExpenseCategory> = emptyList())

    val lists: StateFlow<Lists> = combine(expenses.rules, repository.expenseCategories, ::Lists)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Lists())

    /** Patterns per category id. */
    val patternCounts: StateFlow<Map<Long, Int>> = expenses.rules
        .map { rules -> rules.mapNotNull { it.categoryId }.groupingBy { it }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The "test a statement line" box. */
    val testField = TextFieldState()

    /** The pattern the test text matches, and its category; null without a match (or text). */
    fun testMatch(lists: Lists = this.lists.value): TestMatch? {
        val text = testField.text.toString()
        if (text.isBlank()) return null
        val rule = Categorizer(lists.rules).match(text) ?: return null
        val category = lists.categories.firstOrNull { it.id == rule.categoryId } ?: return null
        return TestMatch(rule.keyword, category.id, category.name, category.countsAsSpending)
    }

    private val _reapplied = MutableStateFlow<Int?>(null)

    /** Set when saved transactions were re-categorized after a change here (how many). */
    val reapplied: StateFlow<Int?> = _reapplied.asStateFlow()

    fun reappliedShown() {
        _reapplied.value = null
    }

    fun addInGroup(name: String, group: Int) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { repository.addExpenseCategory(trimmed, isIncome = group == INCOME) }
    }

    /** Income patterns only match money in, so switching sides re-sorts saved transactions. */
    fun setGroup(id: Long, group: Int) {
        viewModelScope.launch {
            repository.setIncome(id, group == INCOME)
            _reapplied.value = expenses.reapplyRules()
        }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { repository.reorderExpenseCategories(ids) }
    }

    fun setCountsAsSpending(id: Long, counts: Boolean) {
        viewModelScope.launch { repository.setCountsAsSpending(id, counts) }
    }

    override suspend fun insert(name: String) = repository.addExpenseCategory(name)
    override suspend fun update(id: Long, name: String) = repository.renameExpenseCategory(id, name)
    override suspend fun remove(id: Long) {
        repository.deleteExpenseCategory(id)
        // Its patterns went with it: saved transactions may now match another one.
        _reapplied.value = expenses.reapplyRules()
    }

    companion object {
        const val SPENDING = 0
        const val INCOME = 1

        val Factory = appViewModelFactory { CategoriesViewModel(it.catalogRepository, it.expenseRepository) }
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
    onOpen: (categoryId: Long) -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val counts by viewModel.patternCounts.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val reapplied by viewModel.reapplied.collectAsStateWithLifecycle()
    ReappliedMessage(reapplied, viewModel::reappliedShown)
    val noPatterns = stringResource(R.string.category_no_patterns)
    val withCounts = items.map { item ->
        val count = counts[item.id] ?: 0
        item.copy(detail = if (count == 0) noPatterns else pluralStringResource(R.plurals.category_patterns, count, count))
    }
    NameListScreen(
        title = stringResource(R.string.settings_categories_title),
        addLabel = stringResource(R.string.category_add),
        deleteMessage = stringResource(R.string.category_delete_message),
        items = withCounts,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        intro = stringResource(R.string.categories_intro),
        toggle = NameListToggle(
            label = stringResource(R.string.category_counts_as_spending),
            offText = stringResource(R.string.category_not_counted),
            onToggle = viewModel::setCountsAsSpending,
        ),
        onReorder = viewModel::reorder,
        groups = NameListGroups(
            titles = listOf(stringResource(R.string.category_group_spending), stringResource(R.string.category_group_income)),
            onAdd = viewModel::addInGroup,
            onChangeGroup = viewModel::setGroup,
        ),
        onOpen = onOpen,
        header = { TestLineBox(viewModel.testField, viewModel.testMatch(lists), onOpen) },
    )
}

/** Type a statement line: which pattern and category it gets. Tapping the answer opens that category. */
@Composable
private fun TestLineBox(field: TextFieldState, match: TestMatch?, onOpen: (Long) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                state = field,
                label = { Text(stringResource(R.string.rules_test_label)) },
                placeholder = { Text("TWINT *COOP-4521 ZUERICH") },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
            if (field.text.isNotBlank()) {
                Text(
                    match?.let {
                        stringResource(
                            if (it.countsAsSpending) R.string.rules_test_match else R.string.rules_test_match_not_counted,
                            it.categoryName,
                            it.keyword,
                        )
                    } ?: stringResource(R.string.rules_test_no_match),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .then(if (match != null) Modifier.clickable { onOpen(match.categoryId) } else Modifier),
                )
            }
        }
    }
}

/** "3 transactions re-categorized" after a change; nothing when none changed. */
@Composable
internal fun ReappliedMessage(count: Int?, onShown: () -> Unit) {
    val messages = LocalAppMessages.current
    val text = count?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.patterns_reapplied, it, it) }
    LaunchedEffect(count) {
        if (count == null) return@LaunchedEffect
        text?.let { messages.show(it) }
        onShown()
    }
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
