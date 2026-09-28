package io.github.codenextdoor.wealth.backfill

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.Expense
import io.github.codenextdoor.wealth.imports.ImportKeys
import io.github.codenextdoor.wealth.imports.ImportViewModel
import io.github.codenextdoor.wealth.imports.NativePdfText
import io.github.codenextdoor.wealth.imports.ParsedStatement
import io.github.codenextdoor.wealth.imports.StatementFile
import io.github.codenextdoor.wealth.imports.StatementFileKind
import io.github.codenextdoor.wealth.imports.StatementHistory
import io.github.codenextdoor.wealth.imports.StatementSource
import io.github.codenextdoor.wealth.imports.guessAccount
import io.github.codenextdoor.wealth.imports.matchRecurring
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate

enum class BackfillStage { PICKING, REVIEW, SAVING, DONE }

enum class BackfillStatus { READING, READY, UNREADABLE, UNKNOWN_LAYOUT, PDF_NOT_SUPPORTED, NO_HISTORY }

/** One chosen statement file and what it adds. */
data class BackfillFile(
    /** The file's address; identifies it. */
    val key: String,
    val name: String,
    val status: BackfillStatus,
    val format: String = "",
    /** The period its history covers. */
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** Balances it adds to the account's history. */
    val pointCount: Int = 0,
    /** Its balance doesn't match its own details (e.g. holdings × prices). */
    val valueNeedsCheck: Boolean = false,
    /** Null leaves the file out. */
    val accountId: Long? = null,
    /** Accounts it fits: in its currency, and of its kind (bank, card, shares). */
    val accounts: List<Account> = emptyList(),
)

data class BackfillUiState(
    val stage: BackfillStage = BackfillStage.PICKING,
    val files: List<BackfillFile> = emptyList(),
    /** Also add the statements' spending as expenses. */
    val addExpenses: Boolean = false,
    /** Balances saving would add, over files with an account. */
    val pointCount: Int = 0,
    // Set once saved.
    val savedPoints: Int? = null,
    val savedAccounts: Int? = null,
    val savedExpenses: Int? = null,
)

/**
 * Builds account history from many old statements at once: each statement's
 * month-end and closing balances (see [StatementHistory]) go into its
 * account's balance history, and optionally its spending into expenses.
 */
class BackfillViewModel(
    private val appLock: AppLock,
    private val reader: StatementSource,
    private val accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    private val shareRepository: ShareRepository,
    private val expenseRepository: ExpenseRepository,
    private val rateUpdater: RateUpdater,
    private val priceUpdater: PriceUpdater,
    /** Outlives the screen: downloading rates for the new dates finishes after it closes. */
    private val backgroundScope: CoroutineScope,
) : ViewModel() {

    /** A file as read: its statement, or why there's none. */
    private class Loaded(val key: String, val name: String, val status: BackfillStatus, val file: StatementFile? = null, val statement: ParsedStatement? = null)

    private val loaded = MutableStateFlow<List<Loaded>>(emptyList())
    private val chosenAccounts = MutableStateFlow<Map<String, Long?>>(emptyMap())
    private val addExpenses = MutableStateFlow(false)
    private val progress = MutableStateFlow(Progress())

    private data class Progress(
        val stage: BackfillStage = BackfillStage.PICKING,
        val savedPoints: Int? = null,
        val savedAccounts: Int? = null,
        val savedExpenses: Int? = null,
    )

    private data class Catalog(val accounts: List<Account>, val liabilityTypes: Set<Long>, val decimals: Map<String, Int>, val base: String)

    private val catalog = combine(
        accountRepository.accounts,
        catalogRepository.accountTypes,
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
    ) { accounts, types, currencies, base ->
        Catalog(accounts, types.filter { it.kind == AssetKind.LIABILITY }.map { it.id }.toSet(), currencies.associate { it.code to it.decimals }, base)
    }

    val uiState: StateFlow<BackfillUiState> = combine(loaded, chosenAccounts, addExpenses, progress, catalog) { files, chosen, expenses, progress, catalog ->
        val rows = files.map { f ->
            val statement = f.statement
            val points = statement?.let(StatementHistory::points).orEmpty()
            val fitting = statement?.let { s -> catalog.accounts.filter { fits(it, s, catalog.liabilityTypes) } }.orEmpty()
            BackfillFile(
                key = f.key,
                name = f.name,
                status = f.status,
                format = statement?.format.orEmpty(),
                from = points.minOfOrNull { it.date },
                to = points.maxOfOrNull { it.date },
                pointCount = points.size,
                valueNeedsCheck = statement?.valueNeedsCheck == true,
                accountId = if (f.key in chosen) chosen[f.key] else null,
                accounts = fitting,
            )
        }
        BackfillUiState(
            stage = progress.stage,
            files = rows,
            addExpenses = expenses,
            pointCount = rows.filter { it.accountId != null && it.status == BackfillStatus.READY }.sumOf { it.pointCount },
            savedPoints = progress.savedPoints,
            savedAccounts = progress.savedAccounts,
            savedExpenses = progress.savedExpenses,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackfillUiState())

    /** Opening the system file picker shouldn't trigger the app lock on return. */
    fun beforeFilePicker() = appLock.allowBriefExit()

    /** Reads the chosen files (one after another); files already chosen are skipped. */
    fun load(uris: List<Uri>) {
        val fresh = uris.map { it.toString() }.distinct().filter { key -> loaded.value.none { it.key == key } }
        if (fresh.isEmpty()) {
            if (loaded.value.isEmpty()) progress.update { it.copy(stage = BackfillStage.PICKING) }
            return
        }
        progress.update { it.copy(stage = BackfillStage.REVIEW) }
        loaded.update { it + fresh.map { key -> Loaded(key, key.toUri().lastPathSegment ?: key, BackfillStatus.READING) } }
        viewModelScope.launch {
            fresh.forEach { key ->
                val result = read(key)
                // The guessed account first, so a file never shows as ready without one.
                if (result.status == BackfillStatus.READY) {
                    val c = catalog.first()
                    val guess = guessAccount(c.accounts, c.liabilityTypes, result.file!!, result.statement)
                    chosenAccounts.update { it + (key to guess) }
                }
                loaded.update { list -> list.map { if (it.key == key) result else it } }
            }
        }
    }

    private suspend fun read(key: String): Loaded {
        val file = try {
            reader.read(key.toUri())
        } catch (e: NativePdfText.NotSupported) {
            return Loaded(key, key.toUri().lastPathSegment ?: key, BackfillStatus.PDF_NOT_SUPPORTED)
        } ?: return Loaded(key, key.toUri().lastPathSegment ?: key, BackfillStatus.UNREADABLE)
        // CSV exports list transactions but no balances: nothing for the history.
        if (file.kind == StatementFileKind.CSV) return Loaded(key, file.name, BackfillStatus.NO_HISTORY, file)
        val parser = ImportViewModel.STATEMENT_PARSERS.firstOrNull { it.canParse(file.text) }
            ?: return Loaded(key, file.name, BackfillStatus.UNKNOWN_LAYOUT, file)
        val statement = parser.parse(file.text)
        val status = if (StatementHistory.points(statement).isEmpty()) BackfillStatus.NO_HISTORY else BackfillStatus.READY
        return Loaded(key, file.name, status, file, statement)
    }

    /** Same test as the guess: currency, and bank / card / share account. */
    private fun fits(account: Account, statement: ParsedStatement, liabilityTypes: Set<Long>): Boolean {
        val kindFits = if (statement.holdings != null) account.shareSymbol != null else (account.accountTypeId in liabilityTypes) == statement.fromCard
        return (statement.currency == null || account.currencyCode == statement.currency) && kindFits
    }

    fun setAccount(key: String, accountId: Long?) = chosenAccounts.update { it + (key to accountId) }

    fun setAddExpenses(value: Boolean) {
        addExpenses.value = value
    }

    fun save() {
        // The latest choices, not [uiState]: that updates a moment after a change.
        val files = loaded.value
        val chosen = chosenAccounts.value
        val withSpending = addExpenses.value
        viewModelScope.launch {
            progress.update { it.copy(stage = BackfillStage.SAVING) }
            val c = catalog.first()
            val categorizer = Categorizer(expenseRepository.rules())
            var points = 0
            var expenses = 0
            val touched = mutableSetOf<Long>()
            files.filter { it.status == BackfillStatus.READY }.forEach { file ->
                val account = c.accounts.firstOrNull { it.id == chosen[file.key] } ?: return@forEach
                val statement = file.statement ?: return@forEach
                val decimals = c.decimals[account.currencyCode] ?: 2
                val isLiability = account.accountTypeId in c.liabilityTypes
                StatementHistory.points(statement).forEach { point ->
                    val amount = if (isLiability) point.amount.abs() else point.amount
                    val minor = amount.movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
                    accountRepository.addHistoryEntry(account.id, point.date, minor, point.units)
                    points++
                }
                val symbol = account.shareSymbol
                val price = statement.holdings?.price
                val day = statement.closingDate
                if (symbol != null && price != null && day != null) shareRepository.saveFetchedPrices(symbol, mapOf(day to price))
                if (withSpending) expenses += addSpending(statement, account, decimals, categorizer)
                touched += account.id
            }
            progress.value = Progress(BackfillStage.DONE, points, touched.size, expenses)
            // Rates and prices for the newly added past days, so the history chart uses them.
            backgroundScope.launch {
                rateUpdater.refresh()
                priceUpdater.refresh()
            }
        }
    }

    /**
     * Adds the statement's spending like the Import screen would by default:
     * money out, not matching a "don't import" rule, not imported before, not
     * already added by a recurring expense, and not flagged for checking.
     */
    private suspend fun addSpending(statement: ParsedStatement, account: Account, decimals: Int, categorizer: Categorizer): Int {
        val keys = ImportKeys.forTransactions(statement.transactions, account.id)
        val recurring = matchRecurring(expenseRepository, statement, account.id, account.currencyCode, decimals)
        val rows = statement.transactions.mapIndexedNotNull { index, t ->
            val rule = categorizer.match(t.description)
            if (t.amount.signum() >= 0 || t.needsCheck || rule?.skipsImport == true || index in recurring) return@mapIndexedNotNull null
            Expense(
                id = 0,
                date = t.date,
                amountMinor = t.amount.negate().movePointRight(decimals).setScale(0, RoundingMode.HALF_EVEN).longValueExact(),
                currencyCode = account.currencyCode,
                description = t.description,
                categoryId = rule?.categoryId,
                categoryLocked = false,
                accountId = account.id,
                note = null,
            ) to keys[index]
        }
        return expenseRepository.importExpenses(rows)
    }

    companion object {
        val Factory = appViewModelFactory {
            BackfillViewModel(
                it.appLock, it.statementFileReader, it.accountRepository, it.catalogRepository, it.currencyRepository,
                it.shareRepository, it.expenseRepository, it.rateUpdater, it.priceUpdater, it.applicationScope,
            )
        }
    }
}
