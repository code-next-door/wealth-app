package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.backfill.BackfillStage
import io.github.codenextdoor.wealth.backfill.BackfillStatus
import io.github.codenextdoor.wealth.backfill.BackfillViewModel
import io.github.codenextdoor.wealth.data.rates.PriceSource
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.Quote
import io.github.codenextdoor.wealth.data.rates.RateSource
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.imports.StatementFile
import io.github.codenextdoor.wealth.imports.StatementFileKind
import io.github.codenextdoor.wealth.imports.StatementSource
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.TestStatements
import io.github.codenextdoor.wealth.backfill.BackfillUiState
import io.github.codenextdoor.wealth.imports.ImportViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackfillViewModelTest : DatabaseTest() {

    private val shares by lazy { ShareRepository(db) }
    private val statementDay = LocalDate.of(2026, 3, 31)

    private val files = mapOf(
        "hdfc" to StatementFile("HDFC Bank Account Statement.pdf", StatementFileKind.PDF, TestStatements.hdfc()),
        "ubs" to StatementFile("UBS Account Statement.pdf", StatementFileKind.PDF, TestStatements.ubsAccount(statementDay)),
        "swisscard" to StatementFile("c0ffee.pdf", StatementFileKind.PDF, TestStatements.swisscard(statementDay)),
        "stockplan" to StatementFile("Quarterly Statement.pdf", StatementFileKind.PDF, TestStatements.morganStanley(statementDay)),
        "ibkr" to StatementFile("U00000000_2025.pdf", StatementFileKind.PDF, TestStatements.ibkr()),
        "zerodha" to StatementFile("holdings-AB0000.xlsx", StatementFileKind.XLSX, TestStatements.zerodhaHoldings()),
        // Monthly mutual fund statements (CAS): one value each, at the month's end.
        "cas_jun" to StatementFile("cas_detailed_report.pdf", StatementFileKind.PDF, TestStatements.mutualFundCas("01-Jun-2026", "30-Jun-2026")),
        "cas_jul" to StatementFile("cas_detailed_report (1).pdf", StatementFileKind.PDF, TestStatements.mutualFundCas("01-Jul-2026", "31-Jul-2026")),
        // Monthly VIAC pillar 3a reports: one value each, on the reporting day.
        "viac_jul" to StatementFile("2026-09-30_Manual Reporting_1.pdf", StatementFileKind.PDF, TestStatements.viac("31.07.2026")),
        "viac_aug" to StatementFile("2026-09-30_Manual Reporting_2.pdf", StatementFileKind.PDF, TestStatements.viac("31.08.2026")),
        "other" to StatementFile("other.pdf", StatementFileKind.PDF, "Some other bank\n01.01.26 Coffee 4.50"),
        "csv" to StatementFile("export.csv", StatementFileKind.CSV, TestStatements.bankCsv(today)),
    )

    private val source = object : StatementSource {
        override suspend fun read(uri: Uri): StatementFile? = files[uri.lastPathSegment]
    }

    private val noRates = object : RateSource {
        override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate) = emptyMap<String, Quote>()
    }
    private val noPrices = object : PriceSource {
        override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate) = emptyMap<LocalDate, BigDecimal>()
    }

    private fun viewModel() = BackfillViewModel(
        AppLock(SettingsStore(context, "lock_backfill_${System.nanoTime()}")),
        source, accounts, catalog, currencies, shares, expenses,
        RateUpdater(currencies, accounts, noRates) { today },
        PriceUpdater(shares, accounts, noPrices) { today },
        CoroutineScope(Dispatchers.Unconfined),
    ).cancelledAfterTest()

    private fun uri(name: String) = "content://test/$name".toUri()

    private fun setUpAccounts(): Map<String, Long> = runBlocking {
        val stockPlanType = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", stockPlanType.id, "USD", null, 0, Instant.EPOCH, "Morgan Stanley", null, shareSymbol = "GOOG", units = BigDecimal.ZERO),
            balanceDate = today, recordBalance = true,
        )
        mapOf(
            "hdfc" to addAccount("NRO", typeSeedKey = "in_nro", currency = "INR", countrySeedKey = "in", institution = "HDFC"),
            "ubs" to addAccount("Salary", institution = "UBS"),
            "swisscard" to addAccount("Cashback card", typeSeedKey = "credit_card", countrySeedKey = null, institution = "Swisscard"),
            "ibkr" to addAccount("Brokerage", typeSeedKey = "ch_brokerage", institution = "Interactive Brokers"),
            "zerodha" to addAccount("Zerodha", typeSeedKey = "in_stocks", currency = "INR", countrySeedKey = "in", institution = "Zerodha"),
            // Named so nothing in the statement matches it: the account type alone must lead there,
            // not to the first INR account (NRO).
            "cas" to addAccount("Funds", typeSeedKey = "in_mutual_funds", currency = "INR", countrySeedKey = "in"),
            // Likewise: its type, not the first CHF account (Salary), must lead there.
            "viac" to addAccount("Retirement", typeSeedKey = "ch_pillar3a"),
            "stockplan" to accounts.accounts.first().single { it.shareSymbol != null }.id,
        )
    }

    @Test
    fun readsManyStatementsGuessesAccountsAndCountsHistory() {
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(files.keys.map(::uri))
        val state = vm.uiState.await { s -> s.stage == BackfillStage.REVIEW && s.files.size == files.size && s.files.none { it.status == BackfillStatus.READING } }
        val byName = state.files.associateBy { it.key.substringAfterLast("/") }
        listOf("hdfc", "ubs", "swisscard", "stockplan", "ibkr", "zerodha", "cas_jun", "cas_jul", "viac_jul", "viac_aug").forEach { name ->
            assertEquals(name, BackfillStatus.READY, byName.getValue(name).status)
            assertEquals(name, ids.getValue(name.substringBefore("_")), byName.getValue(name).accountId)
        }
        assertEquals(3, byName.getValue("hdfc").pointCount) // three month-ends
        assertEquals(2, byName.getValue("ibkr").pointCount) // start and end
        assertEquals(LocalDate.of(2025, 4, 30), byName.getValue("hdfc").from) // the first month-end
        assertEquals(BackfillStatus.UNKNOWN_LAYOUT, byName.getValue("other").status)
        assertEquals(BackfillStatus.NO_HISTORY, byName.getValue("csv").status) // CSV exports have no balances
        // Only accounts that fit are offered: the card statement gets liability accounts.
        assertEquals(listOf(ids.getValue("swisscard")), byName.getValue("swisscard").accounts.map { it.id })
        assertEquals(1, byName.getValue("zerodha").pointCount) // one snapshot
        assertEquals(1, byName.getValue("cas_jul").pointCount)
        assertEquals(3 + 1 + 1 + 1 + 2 + 1 + 2 + 2, state.pointCount)
    }

    @Test
    fun savingAddsHistoryToEachAccount() = runBlocking {
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(listOf("hdfc", "swisscard", "stockplan", "ibkr").map(::uri))
        vm.uiState.await { s -> s.files.size == 4 && s.files.all { it.status == BackfillStatus.READY } }
        vm.save()
        val done = vm.uiState.await { it.stage == BackfillStage.DONE }
        assertEquals(3 + 1 + 1 + 2, done.savedPoints)
        assertEquals(4, done.savedAccounts)
        assertEquals(0, done.savedExpenses)

        val hdfc = accounts.observeHistory(ids.getValue("hdfc")).first()
        assertTrue(hdfc.any { it.date == LocalDate.of(2025, 5, 31) && it.balanceMinor == 76_720_50L })
        val card = accounts.observeHistory(ids.getValue("swisscard")).first()
        assertTrue(card.any { it.date == statementDay && it.balanceMinor == 1_356_95L }) // owed, positive
        val stockPlan = accounts.observeHistory(ids.getValue("stockplan")).first().first { it.date == statementDay }
        assertEquals(0, BigDecimal("112.5").compareTo(stockPlan.units))
        assertEquals(0, BigDecimal("160").compareTo(shares.prices.first().priceAt("GOOG", statementDay)))
        val ibkr = accounts.observeHistory(ids.getValue("ibkr")).first()
        assertTrue(ibkr.any { it.date == LocalDate.of(2024, 12, 31) && it.balanceMinor == 1_000_00L })
        assertTrue(ibkr.any { it.date == LocalDate.of(2025, 12, 31) && it.balanceMinor == 12_345_60L })
    }

    @Test
    fun monthlyMutualFundStatementsBecomeMonthEndValues() = runBlocking {
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(listOf("cas_jun", "cas_jul").map(::uri))
        vm.uiState.await { s -> s.files.size == 2 && s.files.all { it.status == BackfillStatus.READY } }
        vm.save()
        assertEquals(2, vm.uiState.await { it.stage == BackfillStage.DONE }.savedPoints)
        val history = accounts.observeHistory(ids.getValue("cas")).first()
        assertTrue(history.any { it.date == LocalDate.of(2026, 6, 30) && it.balanceMinor == 96_642_06L })
        assertTrue(history.any { it.date == LocalDate.of(2026, 7, 31) && it.balanceMinor == 96_642_06L })
        assertEquals(0, accounts.observeHistory(ids.getValue("hdfc")).first().count { it.date.year == 2026 && it.date.monthValue in 6..7 })
    }

    @Test
    fun monthlyPillar3aReportsBecomeValuesOnTheirReportingDays() = runBlocking {
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(listOf("viac_jul", "viac_aug").map(::uri))
        vm.uiState.await { s -> s.files.size == 2 && s.files.all { it.status == BackfillStatus.READY } }
        vm.save()
        assertEquals(2, vm.uiState.await { it.stage == BackfillStage.DONE }.savedPoints)
        val history = accounts.observeHistory(ids.getValue("viac")).first()
        assertTrue(history.any { it.date == LocalDate.of(2026, 7, 31) && it.balanceMinor == 9_699_10L })
        assertTrue(history.any { it.date == LocalDate.of(2026, 8, 31) && it.balanceMinor == 9_699_10L })
    }

    @Test
    fun optionallyAddsSpendingButNotRowsThatNeedAHumanLook() = runBlocking {
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(listOf(uri("hdfc")))
        vm.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }
        vm.setAddExpenses(true)
        vm.save()
        val done = vm.uiState.await { it.stage == BackfillStage.DONE }
        // 5 money-out rows; the first row (can't tell in or out) is left for the Import screen.
        assertEquals(5, done.savedExpenses)
        val added = expenses.expensesBetween(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30)).first()
        assertTrue(added.all { it.accountId == ids.getValue("hdfc") && it.currencyCode == "INR" })
        assertTrue(added.none { it.amountMinor == 50_000_00L })

        // Doing it again adds no duplicates.
        val again = viewModel()
        again.load(listOf(uri("hdfc")))
        again.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }
        again.setAddExpenses(true)
        again.save()
        assertEquals(0, again.uiState.await { it.stage == BackfillStage.DONE }.savedExpenses)
    }

    @Test
    fun backfillingTheSameStatementsAgainChangesNothing() = runBlocking {
        // Every kind of statement, with spending and income, twice: the second run adds
        // no balance entries and no transactions, and changes none.
        val ids = setUpAccounts()
        val names = listOf("hdfc", "ubs", "swisscard", "stockplan", "ibkr", "zerodha", "cas_jun", "cas_jul", "viac_jul", "viac_aug")
        suspend fun run(): Int {
            val vm = viewModel()
            vm.load(names.map(::uri))
            vm.uiState.await { s -> s.files.size == names.size && s.files.none { it.status == BackfillStatus.READING } }
            vm.setAddExpenses(true)
            vm.save()
            return vm.uiState.await { it.stage == BackfillStage.DONE }.savedExpenses ?: 0
        }
        // Saving a balance on a day that has one replaces the row (new id, same values).
        suspend fun snapshot() = db.backupDao().let { dao ->
            dao.balanceEntries().map { it.copy(id = 0) }.sortedWith(compareBy({ it.accountId }, { it.date })) to
                dao.expenses().sortedBy { it.id }
        }

        assertTrue(run() > 0)
        val first = snapshot()
        assertTrue(first.second.any { it.amountMinor < 0 }) // money in (salary) came in too
        assertEquals(0, run())
        assertEquals(first, snapshot())
        assertTrue(ids.isNotEmpty())
    }

    @Test
    fun aStatementImportedBeforeIsNotAddedAgainByBackfill() = runBlocking {
        // Import screen first, then Build history with the same file: same fingerprints.
        val ids = setUpAccounts()
        val vm = viewModel()
        vm.load(listOf(uri("ubs")))
        vm.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }
        vm.setAddExpenses(true)
        vm.save()
        val added = vm.uiState.await { it.stage == BackfillStage.DONE }.savedExpenses ?: 0
        assertTrue(added > 0)
        val before = db.backupDao().expenses().size

        val again = viewModel()
        again.load(listOf(uri("ubs")))
        again.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }
        again.setAddExpenses(true)
        again.save()
        assertEquals(0, again.uiState.await { it.stage == BackfillStage.DONE }.savedExpenses)
        assertEquals(before, db.backupDao().expenses().size)
        assertTrue(ids.containsKey("ubs"))
    }

    private suspend fun backfill(vararg names: String): BackfillUiState {
        val vm = viewModel()
        vm.load(names.map(::uri))
        vm.uiState.await { s -> s.files.size == names.size && s.files.none { it.status == BackfillStatus.READING } }
        vm.setAddExpenses(true)
        vm.save()
        return vm.uiState.await { it.stage == BackfillStage.DONE }
    }

    @Test
    fun backfillLeavesOutRowsDeletedBeforeAndSaysSo() = runBlocking {
        setUpAccounts()
        val first = backfill("ubs")
        assertTrue((first.savedExpenses ?: 0) > 0)
        val one = db.backupDao().expenses().first()
        expenses.delete(one.id)

        val again = backfill("ubs")
        assertEquals(0, again.savedExpenses)
        assertEquals(1, again.deletedBefore)
        assertTrue(db.backupDao().expenses().none { it.importKey == one.importKey })
    }

    @Test
    fun backfillLeavesOutRowsAlreadySavedFromAnotherFile() = runBlocking {
        val ids = setUpAccounts()
        // One of the UBS statement's payments, saved before with other text (another file or format).
        val ubs = files.getValue("ubs")
        val parsed = ImportViewModel.STATEMENT_PARSERS.first { it.canParse(ubs.text) }.parse(ubs.text)
        val paid = parsed.transactions.first { it.amount.signum() < 0 }
        expenses.save(expense("Same payment, other text", paid.amount.negate().movePointRight(2).longValueExact(), date = paid.date, accountId = ids.getValue("ubs")))
        val before = db.backupDao().expenses().size

        val done = backfill("ubs")
        assertEquals(1, done.possibleDuplicates)
        val moneyRows = parsed.transactions.count { !it.needsCheck && it.amount.signum() != 0 }
        assertEquals(before + moneyRows - 1, db.backupDao().expenses().size)
    }

    @Test
    fun backfillCountsMoneyInWithoutACategory() = runBlocking {
        setUpAccounts()
        // Without the salary rules, the UBS statement's salary arrives without a category.
        expenses.rules().filter { it.categoryId == categoryId("salary") }.forEach { expenses.deleteRule(it.id) }
        val done = backfill("ubs")
        val added = db.backupDao().expenses()
        assertEquals(1, added.count { it.amountMinor < 0 && it.categoryId == null })
        assertEquals(1, done.uncategorizedIncome)
    }

    @Test
    fun aFileCanBeLeftOut() {
        setUpAccounts()
        val vm = viewModel()
        vm.load(listOf(uri("ibkr")))
        val file = vm.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }.files.single()
        vm.setAccount(file.key, null)
        val state = vm.uiState.await { it.files.single().accountId == null }
        assertEquals(0, state.pointCount)
        assertNull(state.files.single().accountId)
    }

    @Test
    fun savingRightAfterChangingTheAccountUsesTheChange() = runBlocking {
        val ids = setUpAccounts()
        val other = addAccount("Second brokerage", typeSeedKey = "ch_brokerage")
        val vm = viewModel()
        vm.load(listOf(uri("ibkr")))
        val file = vm.uiState.await { s -> s.files.singleOrNull()?.status == BackfillStatus.READY }.files.single()
        assertEquals(ids.getValue("ibkr"), file.accountId)
        vm.setAccount(file.key, other)
        vm.save() // straight away
        vm.uiState.await { it.stage == BackfillStage.DONE }
        assertEquals(2, accounts.observeHistory(other).first().count { it.date.year >= 2024 && it.date.year <= 2025 })
        assertTrue(accounts.observeHistory(ids.getValue("ibkr")).first().none { it.date == LocalDate.of(2025, 12, 31) })
    }
}
