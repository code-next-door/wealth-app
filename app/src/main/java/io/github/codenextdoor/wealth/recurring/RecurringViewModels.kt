package io.github.codenextdoor.wealth.recurring

import io.github.codenextdoor.wealth.ui.Routes
import androidx.navigation.toRoute
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.repository.RecurringRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.Recurrence
import io.github.codenextdoor.wealth.domain.RecurringExpense
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import io.github.codenextdoor.wealth.ui.FormState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.appViewModelFactoryWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One recurring expense in the list. */
data class RecurringRow(
    val id: Long,
    val description: String,
    val amountText: String,
    val intervalMonths: Int,
    val categoryName: String?,
    /** Null once it has ended. */
    val nextDate: LocalDate?,
)

data class RecurringListUiState(val isLoading: Boolean = true, val rows: List<RecurringRow> = emptyList())

class RecurringListViewModel(
    recurringRepository: RecurringRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    today: StateFlow<LocalDate>,
) : ViewModel() {

    val uiState: StateFlow<RecurringListUiState> = combine(
        recurringRepository.recurring,
        catalogRepository.expenseCategories,
        currencyRepository.currencies,
        today,
    ) { items, categories, currencies, today ->
        val names = categories.associate { it.id to it.name }
        val decimals = currencies.associate { it.code to it.decimals }
        RecurringListUiState(
            isLoading = false,
            rows = items.map { item ->
                val dec = decimals[item.currencyCode] ?: 2
                RecurringRow(
                    id = item.id,
                    description = item.description,
                    amountText = formatMoney(minorToDecimal(item.amountMinor, dec), item.currencyCode, dec),
                    intervalMonths = item.intervalMonths,
                    categoryName = item.categoryId?.let(names::get),
                    nextDate = Recurrence.next(item, today),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())

    companion object {
        val Factory = appViewModelFactory { RecurringListViewModel(it.recurringRepository, it.catalogRepository, it.currencyRepository, it.today.date) }
    }
}

/** The form's typed fields (Compose owns the text). */
class RecurringTextFields {
    val description = TextFieldState()
    val amount = TextFieldState()
}

/** The form's choices other than text. */
data class RecurringChoices(
    val currencyCode: String? = null,
    val categoryId: Long? = null,
    /** Once the user picks a category, rules no longer change it. */
    val categoryChosenByUser: Boolean = false,
    val accountId: Long? = null,
    val intervalMonths: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    /** Kept from the saved item, so already-added days aren't added again. */
    val lastAdded: LocalDate? = null,
    val showErrors: Boolean = false,
)

data class RecurringEditUiState(
    val isNew: Boolean = true,
    val isReady: Boolean = false,
    val isFinished: Boolean = false,
    val currencies: List<Currency> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val currencyCode: String = "",
    val categoryId: Long? = null,
    /** The rule that picked [categoryId], if the user didn't. */
    val ruleKeyword: String? = null,
    val accountId: Long? = null,
    val intervalMonths: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val descriptionError: Boolean = false,
    val amountError: Boolean = false,
    val endDateError: Boolean = false,
    /** How many expenses saving adds straight away (days already passed). */
    val dueNow: Int = 0,
    val nextDate: LocalDate? = null,
)

class RecurringEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val recurringRepository: RecurringRepository,
    expenseRepository: ExpenseRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val itemId: Long? = savedStateHandle.toRoute<Routes.RecurringEdit>().recurringId

    val fields = RecurringTextFields()
    private val choices = FormState(RecurringChoices())
    private val status = MutableStateFlow(Status(isReady = false, isFinished = false))

    internal data class Status(val isReady: Boolean, val isFinished: Boolean)

    internal data class Lists(
        val currencies: List<Currency>,
        val categories: List<ExpenseCategory>,
        val accounts: List<Account>,
        val categorizer: Categorizer,
    )

    class Data internal constructor(internal val status: Status, internal val lists: Lists?)

    val data: StateFlow<Data> = combine(
        status,
        combine(currencyRepository.currencies, catalogRepository.expenseCategories, accountRepository.accounts, expenseRepository.rules) { c, cat, a, r ->
            Lists(c, cat, a, Categorizer(r))
        },
    ) { s, l -> Data(s, l) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(status.value, null))

    init {
        viewModelScope.launch {
            val item = itemId?.let { recurringRepository.get(it) }
            if (itemId != null && item == null) {
                status.update { it.copy(isFinished = true) }
                return@launch
            }
            if (item == null) {
                val base = currencyRepository.baseCurrency.first()
                choices.update { it.copy(currencyCode = base) }
            } else {
                val decimals = currencyRepository.currencies.first().firstOrNull { it.code == item.currencyCode }?.decimals ?: 2
                fields.description.setTextAndPlaceCursorAtEnd(item.description)
                fields.amount.setTextAndPlaceCursorAtEnd(minorToInputText(item.amountMinor, decimals))
                choices.value = RecurringChoices(
                    item.currencyCode, item.categoryId, categoryChosenByUser = true, item.accountId, item.intervalMonths,
                    item.startDate, item.endDate, item.lastAdded,
                )
            }
            status.update { it.copy(isReady = true) }
        }
    }

    /** The item the form describes, or null while it's incomplete or invalid. */
    private fun itemOrNull(lists: Lists?, categoryId: Long?): RecurringExpense? {
        val c = choices.value
        val description = fields.description.text.toString().trim()
        val decimals = lists?.currencies?.firstOrNull { it.code == c.currencyCode }?.decimals ?: return null
        val amount = parseAmountToMinor(fields.amount.text.toString(), decimals)?.takeIf { it > 0 } ?: return null
        if (description.isEmpty() || c.currencyCode == null || (c.endDate != null && c.endDate.isBefore(c.startDate))) return null
        return RecurringExpense(itemId ?: 0, description, amount, c.currencyCode, categoryId, c.accountId, c.intervalMonths, c.startDate, c.endDate, c.lastAdded)
    }

    fun uiState(data: Data = this.data.value): RecurringEditUiState {
        val c = choices.value
        val lists = data.lists
        val rule = if (c.categoryChosenByUser) null else lists?.categorizer?.match(fields.description.text.toString())
        val categoryId = if (c.categoryChosenByUser) c.categoryId else rule?.categoryId
        val item = itemOrNull(lists, categoryId)
        val today = LocalDate.now()
        val decimals = lists?.currencies?.firstOrNull { it.code == c.currencyCode }?.decimals ?: 2
        return RecurringEditUiState(
            isNew = itemId == null,
            isReady = data.status.isReady && lists != null,
            isFinished = data.status.isFinished,
            currencies = lists?.currencies.orEmpty(),
            categories = lists?.categories.orEmpty(),
            accounts = lists?.accounts.orEmpty(),
            currencyCode = c.currencyCode.orEmpty(),
            categoryId = categoryId,
            ruleKeyword = rule?.takeIf { it.categoryId != null }?.keyword,
            accountId = c.accountId,
            intervalMonths = c.intervalMonths,
            startDate = c.startDate,
            endDate = c.endDate,
            descriptionError = c.showErrors && fields.description.text.isBlank(),
            amountError = c.showErrors && parseAmountToMinor(fields.amount.text.toString(), decimals)?.takeIf { it > 0 } == null,
            endDateError = c.endDate != null && c.endDate.isBefore(c.startDate),
            dueNow = item?.let { Recurrence.due(it, today).size } ?: 0,
            nextDate = item?.let { Recurrence.next(it, today) },
        )
    }

    fun onCurrencyChange(code: String) = choices.update { it.copy(currencyCode = code) }

    fun onCategoryChange(id: Long?) = choices.update { it.copy(categoryId = id, categoryChosenByUser = true) }

    /** Paying from an account also takes its currency. */
    fun onAccountChange(id: Long?) {
        val currency = id?.let { accountId -> data.value.lists?.accounts?.firstOrNull { it.id == accountId }?.currencyCode }
        choices.update { it.copy(accountId = id, currencyCode = currency ?: it.currencyCode) }
    }

    fun onIntervalChange(months: Int) = choices.update { it.copy(intervalMonths = months) }

    fun onStartDateChange(date: LocalDate) = choices.update { it.copy(startDate = date) }

    fun onEndDateChange(date: LocalDate?) = choices.update { it.copy(endDate = date) }

    fun save() {
        choices.update { it.copy(showErrors = true) }
        val state = uiState()
        val item = itemOrNull(data.value.lists, state.categoryId) ?: return
        viewModelScope.launch {
            recurringRepository.save(item)
            // Days that have already come add their expenses now.
            recurringRepository.addDue(LocalDate.now())
            status.update { it.copy(isFinished = true) }
        }
    }

    fun delete() {
        val id = itemId ?: return
        viewModelScope.launch {
            recurringRepository.delete(id)
            status.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        /** How often it can repeat, in months. */
        val INTERVALS = listOf(1, 3, 6, 12)

        val Factory = appViewModelFactoryWithState { container, handle ->
            RecurringEditViewModel(
                handle, container.recurringRepository, container.expenseRepository, container.catalogRepository,
                container.currencyRepository, container.accountRepository,
            )
        }
    }
}
