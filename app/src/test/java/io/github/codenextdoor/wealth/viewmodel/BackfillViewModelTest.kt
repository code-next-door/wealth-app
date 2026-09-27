package io.github.codenextdoor.wealth.viewmodel

import android.net.Uri
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
        AppLock(context, prefsName = "lock_backfill_test"),
        source, accounts, catalog, currencies, shares, expenses,
        RateUpdater(currencies, accounts, noRates) { today },
        PriceUpdater(shares, accounts, noPrices) { today },
        CoroutineScope(Dispatchers.Unconfined),
    )

    private fun uri(name: String) = Uri.parse("content://test/$name")

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
        listOf("hdfc", "ubs", "swisscard", "stockplan", "ibkr", "zerodha").forEach { name ->
            assertEquals(name, BackfillStatus.READY, byName.getValue(name).status)
            assertEquals(name, ids.getValue(name), byName.getValue(name).accountId)
        }
        assertEquals(3, byName.getValue("hdfc").pointCount) // three month-ends
        assertEquals(2, byName.getValue("ibkr").pointCount) // start and end
        assertEquals(LocalDate.of(2025, 4, 30), byName.getValue("hdfc").from) // the first month-end
        assertEquals(BackfillStatus.UNKNOWN_LAYOUT, byName.getValue("other").status)
        assertEquals(BackfillStatus.NO_HISTORY, byName.getValue("csv").status) // CSV exports have no balances
        // Only accounts that fit are offered: the card statement gets liability accounts.
        assertEquals(listOf(ids.getValue("swisscard")), byName.getValue("swisscard").accounts.map { it.id })
        assertEquals(1, byName.getValue("zerodha").pointCount) // one snapshot
        assertEquals(3 + 1 + 1 + 1 + 2 + 1, state.pointCount)
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
}
