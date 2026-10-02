package io.github.codenextdoor.wealth.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryDetailUiState(
    /** Null until loaded (or once deleted). */
    val category: ExpenseCategory? = null,
    /** Its patterns, A–Z. */
    val patterns: List<CategoryRule> = emptyList(),
    /** Every category, for moving a pattern. */
    val categories: List<ExpenseCategory> = emptyList(),
    /** Set after a change re-sorted saved transactions: how many changed category. */
    val reapplied: Int? = null,
    val deleted: Boolean = false,
    /** Every other category's keywords (keyword → category id): a keyword belongs to one category. */
    val keywordsElsewhere: Map<String, Long> = emptyMap(),
)

/**
 * One category and its patterns (keywords that sort statement lines into it). Every
 * change to what matches applies to saved transactions at once ([ExpenseRepository.
 * reapplyRules]: never those whose category was picked by hand, or split ones).
 */
class CategoryDetailViewModel(
    private val categoryId: Long,
    private val catalog: CatalogRepository,
    private val expenses: ExpenseRepository,
) : ViewModel() {

    private data class Progress(val reapplied: Int? = null, val deleted: Boolean = false)

    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<CategoryDetailUiState> = combine(catalog.expenseCategories, expenses.rules, progress) { categories, rules, p ->
        CategoryDetailUiState(
            category = categories.firstOrNull { it.id == categoryId },
            patterns = rules.filter { it.categoryId == categoryId }.sortedBy { it.keyword },
            categories = categories,
            reapplied = p.reapplied,
            deleted = p.deleted,
            keywordsElsewhere = rules.filter { it.categoryId != null && it.categoryId != categoryId }.associate { it.keyword to it.categoryId!! },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState())

    /** The category [keyword] already belongs to, if another one: saving it here moves it. */
    fun ownerElsewhere(keyword: String): ExpenseCategory? {
        val normalized = Categorizer.normalize(keyword)
        val s = state.value
        val ownerId = s.keywordsElsewhere[normalized] ?: return null
        return s.categories.firstOrNull { it.id == ownerId }
    }

    fun addPattern(keyword: String) = savePattern(null, keyword, categoryId)

    /** Adds ([id] null) or changes a pattern: its keyword, and which category it sorts into. */
    fun savePattern(id: Long?, keyword: String, toCategory: Long) = afterwardsReapply {
        expenses.saveRule(id, keyword, toCategory)
    }

    fun deletePattern(id: Long) = afterwardsReapply { expenses.deleteRule(id) }

    /** A new name; and spending or income (income patterns only match money in, so it re-sorts). */
    fun rename(name: String, income: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val wasIncome = state.value.category?.isIncome
        viewModelScope.launch {
            catalog.renameExpenseCategory(categoryId, trimmed)
            if (wasIncome != null && wasIncome != income) {
                catalog.setIncome(categoryId, income)
                progress.value = progress.value.copy(reapplied = expenses.reapplyRules())
            }
        }
    }

    fun setCountsAsSpending(counts: Boolean) {
        viewModelScope.launch { catalog.setCountsAsSpending(categoryId, counts) }
    }

    /** Its transactions become uncategorized and its patterns go (then others may match them). */
    fun delete() {
        viewModelScope.launch {
            catalog.deleteExpenseCategory(categoryId)
            progress.value = Progress(reapplied = expenses.reapplyRules(), deleted = true)
        }
    }

    fun reappliedShown() {
        progress.value = progress.value.copy(reapplied = null)
    }

    private fun afterwardsReapply(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            progress.value = progress.value.copy(reapplied = expenses.reapplyRules())
        }
    }

    companion object {
        fun factory(categoryId: Long) = appViewModelFactory { CategoryDetailViewModel(categoryId, it.catalogRepository, it.expenseRepository) }
    }
}
