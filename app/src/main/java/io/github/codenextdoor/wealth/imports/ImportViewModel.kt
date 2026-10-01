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
import io.github.codenextdoor.wealth.domain.formatUnits
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class ImportStage { PICKING, LOADING, REVIEW, IMPORTING, ERROR }

enum class ImportError { UNREADABLE, UNKNOWN_PDF, NO_TRANSACTIONS, PDF_NOT_SUPPORTED }

data class ImportRow(
    val index: Int,
    val date: LocalDate,
    val description: String,
    /** Signed, e.g. "−CHF 45.30" for money out. */
    val amountText: String,
    val moneyIn: Boolean,
    val include: Boolean,
    val categoryId: Long?,
    /** The rule that set the category. */
    val ruleKeyword: String?,
    val isDuplicate: Boolean,
    /** Imported before and then deleted by the user. */
    val wasDeleted: Boolean = false,
    /** Not imported from this file, but the account has an expense that day with that amount. */
    val possibleDuplicate: Boolean = false,
    val needsCheck: Boolean,
)

/** What a share account statement says is held, ready to show. */
data class HoldingsView(
    /** "112.5 shares at $160.00". */
    val sharesText: String,
    val cashText: String,
    val totalText: String,
    val date: LocalDate?,
    /** Shares × price + cash equals the statement's total. */
    val addsUp: Boolean,
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
    /** Money in among the ticked rows (salary, refunds…); null when there's none. */
    val includedInText: String? = null,
    /** Set for share account statements, which save holdings instead of expenses. */
    val holdings: HoldingsView? = null,
    /** A statement with a balance but no rows to import (holdings, investment accounts). */
    val balanceOnly: Boolean = false,
    /** Its balance doesn't match its own details. */
    val valueNeedsCheck: Boolean = false,
    /** Set once saved; the screen closes. */
    val importedCount: Int? = null,
    /** The balance of a [balanceOnly] statement was saved. */
    val balanceSaved: Boolean = false,
    /** The holdings were saved (share account statements). */
    val holdingsSaved: Boolean = false,
)

class ImportViewModel(
    private val appLock: io.github.codenextdoor.wealth.security.AppLock,
    private val reader: StatementSource,
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private class Loaded(val file: StatementFile, val csvRows: List<List<String>>)

    private val stage = MutableStateFlow(Stage())
    private data class Stage(
        val stage: ImportStage = ImportStage.PICKING,
        val error: ImportError? = null,
        val imported: Int? = null,
        val holdingsSaved: Boolean = false,
        val balanceSaved: Boolean = false,
    )

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val mapping = MutableStateFlow<CsvMapping?>(null)
    private val accountId = MutableStateFlow<Long?>(null)
    private val includeOverrides = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val categoryOverrides = MutableStateFlow<Map<Int, Long?>>(emptyMap())
    private val recordClosing = MutableStateFlow(true)
    private val known = MutableStateFlow(KnownRows.NONE)


    /** The statement as currently read (CSV depends on the column mapping). */
    private val parsed = combine(loaded, mapping) { loaded, mapping ->
        when {
            loaded == null -> null
            loaded.file.kind != StatementFileKind.CSV -> STATEMENT_PARSERS.firstOrNull { it.canParse(loaded.file.text) }?.parse(loaded.file.text)
            mapping != null -> CsvStatementParser.parse(loaded.csvRows, mapping)
            else -> null
        }
    }.flowOn(Dispatchers.Default) // Long statements take a moment to read; keep it off the main thread.

    private data class Choices(
        val accountId: Long?,
        val include: Map<Int, Boolean>,
        val categories: Map<Int, Long?>,
        val recordClosing: Boolean,
        val known: KnownRows,
    )

    private val choices = combine(accountId, includeOverrides, categoryOverrides, recordClosing, known, ::Choices)

    private data class Catalog(
        val accounts: List<Account>,
        val liabilityTypes: Set<Long>,
        /** Seed key -> type id, for statements that name their account type. */
        val seededTypes: Map<String, Long>,
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
            seededTypes = types.mapNotNull { t -> t.seedKey?.let { it to t.id } }.toMap(),
            categories = categories,
            categorizer = Categorizer(rules, categories.filter { it.isIncome }.map { it.id }.toSet()),
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
                known.value = if (p == null) {
                    KnownRows.NONE
                } else {
                    val c = catalog.first()
                    val currency = c.accounts.firstOrNull { it.id == a }?.currencyCode ?: p.currency ?: c.base
                    knownRows(expenseRepository, p, a, c.decimals[currency] ?: 2)
                }
            }
        }
    }

    fun load(uri: Uri) {
        stage.value = Stage(ImportStage.LOADING)
        viewModelScope.launch {
            val file = try {
                reader.read(uri)
            } catch (e: NativePdfText.NotSupported) {
                stage.value = Stage(ImportStage.ERROR, ImportError.PDF_NOT_SUPPORTED)
                return@launch
            }
            if (file == null) {
                stage.value = Stage(ImportStage.ERROR, ImportError.UNREADABLE)
                return@launch
            }
            val csvRows = if (file.kind == StatementFileKind.CSV) CsvReader.read(file.text) else emptyList()
            val guessed = if (file.kind == StatementFileKind.CSV) CsvStatementParser.guessMapping(csvRows) else null
            val error = when {
                file.kind != StatementFileKind.CSV && STATEMENT_PARSERS.none { it.canParse(file.text) } -> ImportError.UNKNOWN_PDF
                file.kind == StatementFileKind.CSV && guessed == null -> ImportError.NO_TRANSACTIONS
                else -> null
            }
            if (error != null) {
                stage.value = Stage(ImportStage.ERROR, error)
                return@launch
            }
            mapping.value = guessed
            loaded.value = Loaded(file, csvRows)
            accountId.value = guessAccount(file, parsed.first())
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
        viewModelScope.launch {
            val statement = parsed.first() ?: return@launch
            val catalog = catalog.first()
            // Worked out from the latest choices, not [uiState]: that updates a moment after a
            // change, so a quick "Import" could otherwise save a category the user just changed.
            val state = build(stage.value, loaded.value, mapping.value, statement, choices.first(), catalog)
            stage.update { it.copy(stage = ImportStage.IMPORTING) }
            statement.holdings?.let { holdings ->
                saveHoldings(statement, holdings, state, catalog)
                return@launch
            }
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
            val balanceOnly = statement.transactions.isEmpty()
            if ((state.recordClosingBalance || balanceOnly) && account != null && closing != null && closingDate != null) {
                val isLiability = account.accountTypeId in catalog.liabilityTypes
                val value = if (isLiability) closing.abs() else closing
                val minor = value.movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
                accountRepository.addHistoryEntry(account.id, closingDate, minor)
            }
            expenseRepository.showMonthRequest.value = statement.transactions.maxOfOrNull { it.date }?.let(YearMonth::from)
            stage.value = Stage(ImportStage.REVIEW, imported = added, balanceSaved = balanceOnly && account != null && closing != null)
        }
    }

    /** Shares and cash on the statement's closing day, and that day's price. */
    private suspend fun saveHoldings(statement: ParsedStatement, holdings: Holdings, state: ImportUiState, catalog: Catalog) {
        val account = state.accounts.firstOrNull { it.id == state.accountId }
        val symbol = account?.shareSymbol
        val date = statement.closingDate
        if (account == null || symbol == null || date == null) {
            stage.value = Stage(ImportStage.REVIEW)
            return
        }
        val decimals = catalog.decimals[account.currencyCode] ?: 2
        val cashMinor = holdings.cash.movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
        accountRepository.addHistoryEntry(account.id, date, cashMinor, holdings.units)
        // Like a downloaded price: the statement's, so a price the user typed for that day still wins.
        shareRepository.saveFetchedPrices(symbol, mapOf(date to holdings.price))
        stage.value = Stage(ImportStage.REVIEW, imported = 0, holdingsSaved = true)
    }

    private suspend fun guessAccount(file: StatementFile, statement: ParsedStatement?): Long? =
        catalog.first().let { guessAccount(accountRepository.accounts.first(), it.liabilityTypes, file, statement, it.seededTypes) }

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
            val moneyIn = t.amount.signum() > 0
            val rule = catalog.categorizer.match(t.description, moneyOut = !moneyIn)
            val duplicate = choices.known.isImported(index)
            val deleted = choices.known.wasRemoved(index)
            val possibleDuplicate = index in choices.known.possibleDuplicates
            val sign = if (moneyIn) "+" else "−"
            ImportRow(
                index = index,
                date = t.date,
                description = t.description,
                amountText = sign + formatMoney(t.amount.abs(), currency, decimals),
                moneyIn = moneyIn,
                // By default every new row, both ways (salary is income, a refund lowers its
                // category, a card payment is listed but not counted), except: imported before or
                // deleted since, or probably saved from another file.
                include = choices.include[index] ?: (!duplicate && !deleted && !possibleDuplicate),
                categoryId = if (index in choices.categories) {
                    choices.categories[index]
                } else {
                    importCategory(t, parsed.fromCard, catalog.categorizer, catalog.categories)
                },
                ruleKeyword = rule?.keyword,
                isDuplicate = duplicate,
                wasDeleted = deleted,
                possibleDuplicate = possibleDuplicate,
                needsCheck = t.needsCheck,
            )
        }.orEmpty()

        val included = rows.filter { it.include }
        val amounts = included.map { parsed!!.transactions[it.index].amount }
        val moneyOut = amounts.filter { it.signum() < 0 }.fold(BigDecimal.ZERO) { sum, a -> sum - a }
        val moneyIn = amounts.filter { it.signum() > 0 }.fold(BigDecimal.ZERO, BigDecimal::add)
        val holdings = parsed?.holdings?.let { h ->
            val code = parsed.currency ?: currency
            val dec = catalog.decimals[code] ?: 2
            HoldingsView(
                sharesText = "${formatUnits(h.units)} × ${formatMoney(h.price, code, dec)}",
                cashText = formatMoney(h.cash, code, dec),
                totalText = parsed.closingBalance?.let { formatMoney(it, code, dec) }.orEmpty(),
                date = parsed.closingDate,
                addsUp = h.addsUp,
            )
        }
        return ImportUiState(
            stage = stage.stage,
            error = stage.error,
            fileName = loaded?.file?.name.orEmpty(),
            format = parsed?.format.orEmpty(),
            csvHeaders = mapping?.let { loaded?.csvRows?.getOrNull(it.headerRow) }.orEmpty(),
            csvMapping = mapping,
            // A share account statement can only go to an account holding shares.
            accounts = if (holdings != null) catalog.accounts.filter { it.shareSymbol != null } else catalog.accounts,
            accountId = choices.accountId,
            categories = catalog.categories,
            currency = currency,
            statementCurrency = parsed?.currency?.takeIf { it != currency },
            rows = rows,
            closingBalanceText = parsed?.closingBalance?.let { formatMoney(it, currency, decimals) },
            closingDate = parsed?.closingDate,
            recordClosingBalance = choices.recordClosing,
            includedCount = included.size,
            includedTotalText = formatMoney(moneyOut, currency, decimals),
            includedInText = moneyIn.takeIf { it.signum() > 0 }?.let { formatMoney(it, currency, decimals) },
            holdings = holdings,
            balanceOnly = parsed != null && parsed.transactions.isEmpty() && parsed.holdings == null && parsed.closingBalance != null,
            valueNeedsCheck = parsed?.valueNeedsCheck == true,
            importedCount = stage.imported,
            balanceSaved = stage.balanceSaved,
            holdingsSaved = stage.holdingsSaved,
        )
    }

    companion object {
        /** Readers for statement layouts (PDFs and spreadsheets); CSV is handled separately. */
        val STATEMENT_PARSERS: List<StatementParser> = listOf(
            UbsAccountStatementParser(), UbsCardStatementParser(), UbsCardTransactionsParser(), SwisscardStatementParser(),
            MorganStanleyStatementParser(), HdfcStatementParser(), IbkrActivityStatementParser(), ZerodhaHoldingsParser(),
            MutualFundCasParser(), ViacStatementParser(), HdfcNetBankingParser(), HdfcCardStatementParser(),
        )

        val Factory = appViewModelFactory {
            ImportViewModel(
                it.appLock, it.statementFileReader, it.expenseRepository, it.accountRepository, it.catalogRepository, it.currencyRepository,
                it.shareRepository,
            )
        }
    }
}
