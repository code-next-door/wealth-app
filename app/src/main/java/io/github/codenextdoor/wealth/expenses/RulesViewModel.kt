package io.github.codenextdoor.wealth.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [categoryId] null = "don't import"; the UI shows its own label then. */
data class RuleRow(val id: Long, val keyword: String, val categoryId: Long?, val categoryName: String?)

data class RulesUiState(
    val rules: List<RuleRow> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val testText: String = "",
    /** The rule the test text matches; null if none (or no text). */
    val testMatch: RuleRow? = null,
    /** Set after "re-apply": how many expenses changed category. */
    val reappliedCount: Int? = null,
)

class RulesViewModel(
    private val expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    /** The "test a statement line" box. */
    val testField = TextFieldState()
    private val reapplied = MutableStateFlow<Int?>(null)

    /** Database-backed parts of the screen; the test text lives in [testText]. */
    class Data internal constructor(
        internal val rules: List<CategoryRule>,
        internal val categories: List<ExpenseCategory>,
        internal val reapplied: Int?,
    )

    val data: StateFlow<Data> = combine(expenseRepository.rules, catalogRepository.expenseCategories, reapplied, ::Data)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(emptyList(), emptyList(), null))

    /** The full screen state. Reads [testField], so it updates as the user types. */
    fun uiState(data: Data = this.data.value): RulesUiState {
        val names = data.categories.associate { it.id to it.name }
        val rows = data.rules.map { RuleRow(it.id, it.keyword, it.categoryId, it.categoryId?.let(names::get)) }
        val text = testField.text.toString()
        val match: CategoryRule? = if (text.isBlank()) null else Categorizer(data.rules).match(text)
        return RulesUiState(
            rules = rows,
            categories = data.categories,
            testText = text,
            testMatch = match?.let { m -> rows.firstOrNull { it.id == m.id } },
            reappliedCount = data.reapplied,
        )
    }

    fun onTestTextChange(value: String) = testField.setTextAndPlaceCursorAtEnd(value)

    fun save(id: Long?, keyword: String, categoryId: Long?) {
        viewModelScope.launch { expenseRepository.saveRule(id, keyword, categoryId) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { expenseRepository.deleteRule(id) }
    }

    fun reapply() {
        viewModelScope.launch { reapplied.value = expenseRepository.reapplyRules() }
    }

    fun dismissReapplied() {
        reapplied.value = null
    }

    companion object {
        val Factory = appViewModelFactory { RulesViewModel(it.expenseRepository, it.catalogRepository) }
    }
}
