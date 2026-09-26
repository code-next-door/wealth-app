package io.github.codenextdoor.wealth.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.Expense
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class ImportStage { PICKING, LOADING, REVIEW, IMPORTING, ERROR }

enum class ImportError { UNREADABLE, UNKNOWN_PDF, NO_TRANSACTIONS }

data class ImportRow(
    val index: Int,
    val date: LocalDate,
    val description: String,
    /** Signed, e.g. "−CHF 45.30" for money out. */
    val amountText: String,
    val moneyIn: Boolean,
    val include: Boolean,
    val categoryId: Long?,
    /** The rule that set the category or skipped the row. */
    val ruleKeyword: String?,
    val skippedByRule: Boolean,
    val isDuplicate: Boolean,
    val needsCheck: Boolean,
)

data class ImportUiState(
    val stage: ImportStage = ImportStage.PICKING,
    val error: ImportError? = null,
    val fileName: String = "",
    val format: String = "",
    val csvHeaders: List<String> = emptyList(),
    val csvMapping: CsvMapping? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val categories: List<ExpenseCategory> = emptyList(),
    val currency: String = "",
    /** Set when the statement's currency differs from the chosen account's. */
    val statementCurrency: String? = null,
    val rows: List<ImportRow> = emptyList(),
    val closingBalanceText: String? = null,
    val closingDate: LocalDate? = null,
    val recordClosingBalance: Boolean = true,
    val includedCount: Int = 0,
    val includedTotalText: String = "",
    /** Set once saved; the screen closes. */
    val importedCount: Int? = null,
)

class ImportViewModel(
    private val appLock: io.github.codenextdoor.wealth.security.AppLock,
    private val reader: StatementSource,
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private class Loaded(val file: StatementFile, val csvRows: List<List<String>>)

    private val stage = MutableStateFlow(Stage())
    private data class Stage(
        val stage: ImportStage = ImportStage.PICKING,
        val error: ImportError? = null,
        val imported: Int? = null,
    )

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val mapping = MutableStateFlow<CsvMapping?>(null)
    private val accountId = MutableStateFlow<Long?>(null)
    private val includeOverrides = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val categoryOverrides = MutableStateFlow<Map<Int, Long?>>(emptyMap())
    private val recordClosing = MutableStateFlow(true)
    private val existingKeys = MutableStateFlow<Set<String>>(emptySet())

    /** The statement as currently read (CSV depends on the column mapping). */
    private val parsed = combine(loaded, mapping) { loaded, mapping ->
        when {
            loaded == null -> null
            loaded.file.kind == StatementFileKind.PDF -> PDF_PARSERS.firstOrNull { it.canParse(loaded.file.text) }?.parse(loaded.file.text)
            mapping != null -> CsvStatementParser.parse(loaded.csvRows, mapping)
            else -> null
        }
    }

    private data class Choices(
        val accountId: Long?,
        val include: Map<Int, Boolean>,
        val categories: Map<Int, Long?>,
        val recordClosing: Boolean,
        val existingKeys: Set<String>,
    )

    private val choices = combine(accountId, includeOverrides, categoryOverrides, recordClosing, existingKeys, ::Choices)

    private data class Catalog(
        val accounts: List<Account>,
        val liabilityTypes: Set<Long>,
        val categories: List<ExpenseCategory>,
        val categorizer: Categorizer,
        val decimals: Map<String, Int>,
        val base: String,
    )

    private val catalog = combine(
        accountRepository.accounts,
        catalogRepository.accountTypes,
        catalogRepository.expenseCategories,
        expenseRepository.rules,
        combine(currencyRepository.currencies, currencyRepository.baseCurrency) { c, b -> c to b },
    ) { accounts, types, categories, rules, (currencies, base) ->
        Catalog(
            accounts = accounts,
            liabilityTypes = types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet(),
            categories = categories,
            categorizer = Categorizer(rules),
            decimals = currencies.associate { it.code to it.decimals },
            base = base,
        )
    }

    val uiState: StateFlow<ImportUiState> = combine(
        stage,
        combine(loaded, mapping) { l, m -> l to m },
        parsed,
        choices,
        catalog,
    ) { stage, (loaded, mapping), parsed, choices, catalog ->
        build(stage, loaded, mapping, parsed, choices, catalog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    init {
        // Re-check duplicates whenever the rows or the target account change.
        viewModelScope.launch {
            combine(parsed, accountId) { p, a -> p to a }.collectLatest { (p, a) ->
                existingKeys.value = if (p == null) emptySet() else expenseRepository.existingImportKeys(ImportKeys.forTransactions(p.transactions, a))
            }
        }
    }

    fun load(uri: Uri) {
        stage.value = Stage(ImportStage.LOADING)
        viewModelScope.launch {
            val file = reader.read(uri)
            if (file == null) {
                stage.value = Stage(ImportStage.ERROR, ImportError.UNREADABLE)
                return@launch
            }
            val csvRows = if (file.kind == StatementFileKind.CSV) CsvReader.read(file.text) else emptyList()
            val guessed = if (file.kind == StatementFileKind.CSV) CsvStatementParser.guessMapping(csvRows) else null
            val error = when {
                file.kind == StatementFileKind.PDF && PDF_PARSERS.none { it.canParse(file.text) } -> ImportError.UNKNOWN_PDF
                file.kind == StatementFileKind.CSV && guessed == null -> ImportError.NO_TRANSACTIONS
                else -> null
            }
            if (error != null) {
                stage.value = Stage(ImportStage.ERROR, error)
                return@launch
            }
            mapping.value = guessed
            loaded.value = Loaded(file, csvRows)
            accountId.value = guessAccount(file, parsed.first()?.currency)
            stage.value = Stage(ImportStage.REVIEW)
        }
    }

    /** Opening the system file picker shouldn't trigger the app lock on return. */
    fun beforeFilePicker() = appLock.allowBriefExit()

    fun cancelPick() {
        stage.value = Stage(ImportStage.ERROR, null)
    }

    fun selectAccount(id: Long?) {
        accountId.value = id
    }

    fun setInclude(index: Int, include: Boolean) = includeOverrides.update { it + (index to include) }

    fun setCategory(index: Int, categoryId: Long?) {
        categoryOverrides.update { it + (index to categoryId) }
        includeOverrides.update { it + (index to true) }
    }

    fun setRecordClosingBalance(value: Boolean) {
        recordClosing.value = value
    }

    fun updateMapping(transform: (CsvMapping) -> CsvMapping) {
        mapping.update { it?.let(transform) }
        includeOverrides.value = emptyMap()
        categoryOverrides.value = emptyMap()
    }

    fun import() {
        val state = uiState.value
        viewModelScope.launch {
            val statement = parsed.first() ?: return@launch
            val catalog = catalog.first()
            stage.update { it.copy(stage = ImportStage.IMPORTING) }
            val keys = ImportKeys.forTransactions(statement.transactions, state.accountId)
            val decimals = catalog.decimals[state.currency] ?: 2
            val expenses = state.rows.filter { it.include }.map { row ->
                val t = statement.transactions[row.index]
                Expense(
                    id = 0,
                    date = t.date,
                    // Statements show money out as negative; an expense is positive spending.
                    amountMinor = t.amount.negate().movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact(),
                    currencyCode = state.currency,
                    description = t.description,
                    categoryId = row.categoryId,
                    categoryLocked = row.index in categoryOverrides.value,
                    accountId = state.accountId,
                    note = null,
                ) to keys[row.index]
            }
            val added = expenseRepository.importExpenses(expenses)

            val account = state.accounts.firstOrNull { it.id == state.accountId }
            val closing = statement.closingBalance
            val closingDate = statement.closingDate
            if (state.recordClosingBalance && account != null && closing != null && closingDate != null) {
                val isLiability = account.accountTypeId in catalog.liabilityTypes
                val value = if (isLiability) closing.abs() else closing
                val minor = value.movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
                accountRepository.addHistoryEntry(account.id, closingDate, minor)
            }
            expenseRepository.showMonthRequest.value = statement.transactions.maxOfOrNull { it.date }?.let(YearMonth::from)
            stage.value = Stage(ImportStage.REVIEW, imported = added)
        }
    }

    /**
     * The likely target: an asset account (bank statements aren't for cards or
     * loans) in the statement's currency whose name or bank appears in the
     * file; else any such account.
     */
    private suspend fun guessAccount(file: StatementFile, currency: String?): Long? {
        val liabilityTypes = catalog.first().liabilityTypes
        val accounts = accountRepository.accounts.first()
        val inCurrency = accounts.filter { (currency == null || it.currencyCode == currency) && it.accountTypeId !in liabilityTypes }
        val hint = file.name.uppercase() + " " + file.text.take(2000).uppercase()
        return (
            inCurrency.firstOrNull { a -> listOfNotNull(a.institution, a.name).any { it.isNotBlank() && hint.contains(it.uppercase()) } }
                ?: inCurrency.firstOrNull()
            )?.id
    }

    private fun build(
        stage: Stage,
        loaded: Loaded?,
        mapping: CsvMapping?,
        parsed: ParsedStatement?,
        choices: Choices,
        catalog: Catalog,
    ): ImportUiState {
        val account = catalog.accounts.firstOrNull { it.id == choices.accountId }
        val currency = account?.currencyCode ?: parsed?.currency ?: catalog.base
        val decimals = catalog.decimals[currency] ?: 2
        val keys = parsed?.let { ImportKeys.forTransactions(it.transactions, choices.accountId) }.orEmpty()

        val rows = parsed?.transactions?.mapIndexed { index, t ->
            val rule = catalog.categorizer.match(t.description)
            val skipped = rule?.skipsImport == true
            val duplicate = keys.getOrNull(index) in choices.existingKeys
            val moneyIn = t.amount.signum() > 0
            val sign = if (moneyIn) "+" else "−"
            ImportRow(
                index = index,
                date = t.date,
                description = t.description,
                amountText = sign + formatMoney(t.amount.abs(), currency, decimals),
                moneyIn = moneyIn,
                // By default: spending only, not already imported, not a "don't import" match.
                include = choices.include[index] ?: (!moneyIn && !skipped && !duplicate),
                categoryId = if (index in choices.categories) choices.categories[index] else rule?.categoryId,
                ruleKeyword = rule?.keyword,
                skippedByRule = skipped,
                isDuplicate = duplicate,
                needsCheck = t.needsCheck,
            )
        }.orEmpty()

        val included = rows.filter { it.include }
        val total = included.fold(BigDecimal.ZERO) { sum, r -> sum - parsed!!.transactions[r.index].amount }
        return ImportUiState(
            stage = stage.stage,
            error = stage.error,
            fileName = loaded?.file?.name.orEmpty(),
            format = parsed?.format.orEmpty(),
            csvHeaders = mapping?.let { loaded?.csvRows?.getOrNull(it.headerRow) }.orEmpty(),
            csvMapping = mapping,
            accounts = catalog.accounts,
            accountId = choices.accountId,
            categories = catalog.categories,
            currency = currency,
            statementCurrency = parsed?.currency?.takeIf { it != currency },
            rows = rows,
            closingBalanceText = parsed?.closingBalance?.let { formatMoney(it, currency, decimals) },
            closingDate = parsed?.closingDate,
            recordClosingBalance = choices.recordClosing,
            includedCount = included.size,
            includedTotalText = formatMoney(total, currency, decimals),
            importedCount = stage.imported,
        )
    }

    companion object {
        /** Readers for PDF layouts; CSV is handled separately. */
        val PDF_PARSERS: List<StatementParser> = listOf(UbsAccountStatementParser())

        val Factory = appViewModelFactory {
            ImportViewModel(it.appLock, it.statementFileReader, it.expenseRepository, it.accountRepository, it.catalogRepository, it.currencyRepository)
        }
    }
}
