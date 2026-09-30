package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.Expense
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import io.github.codenextdoor.wealth.ui.FormState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The expense form's text fields, owned by Compose's TextFieldState so typing never goes out of sync. */
class ExpenseTextFields {
    val description = TextFieldState()
    val amount = TextFieldState()
    val note = TextFieldState()
}

/** A snapshot of the form: text read from [ExpenseTextFields] plus the choices made. */
data class ExpenseForm(
    val description: String = "",
    val amountText: String = "",
    val currencyCode: String? = null,
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    /** True once the user picks a category (including "Uncategorized"); rules then stop changing it. */
    val categoryChosenByUser: Boolean = false,
    /** Money in (income, or a refund under a spending category) rather than spent. */
    val received: Boolean = false,
    /** True once the user picks Spent/Received; an income category then no longer switches it. */
    val directionChosenByUser: Boolean = false,
    val currencyChosenByUser: Boolean = false,
    val accountId: Long? = null,
    val note: String = "",
    val showErrors: Boolean = false,
)

/** Offered after the user corrects a category: "always use X for keyword?". */
data class RuleSuggestion(val keyword: String, val categoryId: Long, val categoryName: String)

data class ExpenseEditUiState(
    val isNew: Boolean = true,
    val isReady: Boolean = false,
    val form: ExpenseForm = ExpenseForm(),
    val categories: List<ExpenseCategory> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    /** The rule that picked the current category, if a rule did. */
    val matchedRule: CategoryRule? = null,
    val ruleSuggestion: RuleSuggestion? = null,
    val isFinished: Boolean = false,
) {
    val selectedCurrency: Currency? get() = currencies.firstOrNull { it.code == form.currencyCode }
    val amountMinor: Long? get() = selectedCurrency?.let { parseAmountToMinor(form.amountText, it.decimals) }
    val descriptionError get() = form.showErrors && form.description.isBlank()
    val amountError get() = form.showErrors && amountMinor == null
}

class ExpenseEditViewModel(
    /** Null adds a new one. */
    private val expenseId: Long?,
    private val expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {


    val fields = ExpenseTextFields()

    /** Choices other than text (currency, date, category, account, flags); the text parts are unused here. */
    private val form = FormState(ExpenseForm())
    /** Ready once the expense (or, for a new one, its default currency) is loaded. */
    private val status = MutableStateFlow(Status(isReady = false))

    internal data class Status(
        val isReady: Boolean,
        val suggestion: RuleSuggestion? = null,
        val isFinished: Boolean = false,
    )

    private val lists = combine(
        catalogRepository.expenseCategories,
        accountRepository.accounts,
        currencyRepository.currencies,
        expenseRepository.rules,
    ) { categories, accounts, currencies, rules ->
        Lists(categories, accounts, currencies, Categorizer(rules, categories.filter { it.isIncome }.map { it.id }.toSet()))
    }

    internal data class Lists(
        val categories: List<ExpenseCategory>,
        val accounts: List<Account>,
        val currencies: List<Currency>,
        val categorizer: Categorizer,
    )

    /** Database-backed parts of the screen. The form itself lives in [form]. */
    class Data internal constructor(internal val status: Status, internal val lists: Lists?)

    val data: StateFlow<Data> = combine(status, lists) { status, lists -> Data(status, lists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(status.value, null))

    /** The full screen state. Reads [form] (Compose state), so composables recompose as the user types. */
    fun uiState(data: Data = this.data.value): ExpenseEditUiState {
        val status = data.status
        val lists = data.lists
        val form = form.value.copy(
            description = fields.description.text.toString(),
            amountText = fields.amount.text.toString(),
            note = fields.note.text.toString(),
        )
        // Until the user picks a category, it follows the rules as they type.
        val rule = if (form.categoryChosenByUser) null else lists?.categorizer?.match(form.description, moneyOut = !form.received)
        val effectiveForm = if (form.categoryChosenByUser) form else form.copy(categoryId = rule?.categoryId)
        return ExpenseEditUiState(
            isNew = expenseId == null,
            isReady = status.isReady && lists != null,
            form = effectiveForm,
            categories = lists?.categories.orEmpty(),
            accounts = lists?.accounts.orEmpty(),
            currencies = lists?.currencies.orEmpty(),
            matchedRule = rule,
            ruleSuggestion = status.suggestion,
            isFinished = status.isFinished,
        )
    }

    init {
        viewModelScope.launch {
            if (expenseId == null) {
                val base = currencyRepository.baseCurrency.first()
                form.update { if (it.currencyCode == null) it.copy(currencyCode = base) else it }
                status.update { it.copy(isReady = true) }
                return@launch
            }
            val expense = expenseRepository.get(expenseId)
            if (expense == null) {
                status.update { it.copy(isFinished = true) }
                return@launch
            }
            val decimals = currencyRepository.currencies.first().firstOrNull { it.code == expense.currencyCode }?.decimals ?: 2
            fields.description.setTextAndPlaceCursorAtEnd(expense.description)
            // The amount without a sign; Spent/Received says which way.
            fields.amount.setTextAndPlaceCursorAtEnd(minorToInputText(kotlin.math.abs(expense.amountMinor), decimals))
            fields.note.setTextAndPlaceCursorAtEnd(expense.note.orEmpty())
            form.value = ExpenseForm(
                currencyCode = expense.currencyCode,
                date = expense.date,
                categoryId = expense.categoryId,
                categoryChosenByUser = expense.categoryLocked,
                received = expense.amountMinor < 0,
                directionChosenByUser = true,
                currencyChosenByUser = true,
                accountId = expense.accountId,
            )
            status.update { it.copy(isReady = true) }
        }
    }

    // Programmatic edits (the screen edits the text fields directly).
    fun onDescriptionChange(value: String) = fields.description.setTextAndPlaceCursorAtEnd(value)
    fun onAmountChange(value: String) = fields.amount.setTextAndPlaceCursorAtEnd(value)
    fun onNoteChange(value: String) = fields.note.setTextAndPlaceCursorAtEnd(value)
    fun onDateChange(value: LocalDate) = form.update { it.copy(date = value) }

    fun onCurrencyChange(code: String) = form.update { it.copy(currencyCode = code, currencyChosenByUser = true) }

    /** Picking an account also picks its currency, unless the user chose one. */
    fun onAccountChange(accountId: Long?) {
        val account = uiState().accounts.firstOrNull { it.id == accountId }
        form.update {
            if (account != null && !it.currencyChosenByUser) {
                it.copy(accountId = accountId, currencyCode = account.currencyCode)
            } else {
                it.copy(accountId = accountId)
            }
        }
    }

    /**
     * [categoryId] null means "Uncategorized", chosen on purpose. An income category
     * means money in, unless the user already chose Spent or Received.
     */
    fun onCategoryChange(categoryId: Long?) {
        val isIncome = uiState().categories.firstOrNull { it.id == categoryId }?.isIncome == true
        form.update {
            it.copy(
                categoryId = categoryId,
                categoryChosenByUser = true,
                received = if (isIncome && !it.directionChosenByUser) true else it.received,
            )
        }
    }

    fun onDirectionChange(received: Boolean) = form.update { it.copy(received = received, directionChosenByUser = true) }

    fun save() {
        form.update { it.copy(showErrors = true) }
        val state = uiState()
        val f = state.form
        val currency = state.selectedCurrency
        val amount = state.amountMinor
        if (f.description.isBlank() || currency == null || amount == null) return

        viewModelScope.launch {
            expenseRepository.save(
                Expense(
                    id = expenseId ?: 0,
                    date = f.date,
                    // Stored like a statement row: money in negative.
                    amountMinor = kotlin.math.abs(amount).let { if (f.received) -it else it },
                    currencyCode = currency.code,
                    description = f.description.trim(),
                    categoryId = f.categoryId,
                    categoryLocked = f.categoryChosenByUser,
                    accountId = f.accountId,
                    note = f.note.trim().ifEmpty { null },
                ),
            )
            val suggestion = suggestionFor(f)
            // With a suggestion, the screen asks first and closes after the answer.
            status.update { it.copy(suggestion = suggestion, isFinished = suggestion == null) }
        }
    }

    /** A rule to offer when the user picked a category the rules wouldn't have. */
    private suspend fun suggestionFor(f: ExpenseForm): RuleSuggestion? {
        val categoryId = f.categoryId ?: return null
        if (!f.categoryChosenByUser) return null
        if (expenseRepository.categorizer().categoryFor(f.description, moneyOut = !f.received) == categoryId) return null
        val keyword = Categorizer.suggestKeyword(f.description).ifEmpty { return null }
        val name = uiState().categories.firstOrNull { it.id == categoryId }?.name ?: return null
        return RuleSuggestion(keyword, categoryId, name)
    }

    /** Accepts the suggestion (possibly with an edited keyword) and applies it to other expenses. */
    fun acceptRule(keyword: String) {
        val suggestion = status.value.suggestion ?: return
        viewModelScope.launch {
            expenseRepository.saveRule(null, keyword, suggestion.categoryId)
            expenseRepository.reapplyRules()
            status.update { it.copy(suggestion = null, isFinished = true) }
        }
    }

    fun declineRule() = status.update { it.copy(suggestion = null, isFinished = true) }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            expenseRepository.delete(id)
            status.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        fun factory(expenseId: Long?) = appViewModelFactory { container ->
            ExpenseEditViewModel(
                expenseId,
                container.expenseRepository,
                container.catalogRepository,
                container.accountRepository,
                container.currencyRepository,
            )
        }
    }
}
