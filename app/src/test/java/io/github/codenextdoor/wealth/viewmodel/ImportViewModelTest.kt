package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.imports.ImportError
import java.math.BigDecimal
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.imports.ImportStage
import io.github.codenextdoor.wealth.imports.ImportViewModel
import io.github.codenextdoor.wealth.imports.StatementFile
import io.github.codenextdoor.wealth.imports.StatementFileKind
import io.github.codenextdoor.wealth.imports.StatementSource
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import io.github.codenextdoor.wealth.testutil.TestStatements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportViewModelTest : DatabaseTest() {

    private class FakeSource(private val files: Map<String, StatementFile>) : StatementSource {
        override suspend fun read(uri: Uri): StatementFile? = files[uri.toString()]
    }

    private val pdf = "content://test/statement.pdf".toUri()
    private val csv = "content://test/export.csv".toUri()
    private val broken = "content://test/broken".toUri()
    private val otherPdf = "content://test/other.pdf".toUri()
    private val swisscardPdf = "content://test/swisscard.pdf".toUri()
    private val ubsCardPdf = "content://test/invoice.pdf".toUri()
    private val stockPlanPdf = "content://test/quarterly.pdf".toUri()
    private val zerodhaXlsx = "content://test/holdings.xlsx".toUri()
    private val shares by lazy { ShareRepository(db) }

    private fun viewModel() = ImportViewModel(
        AppLock(SettingsStore(context, "lock_import_${System.nanoTime()}")),
        FakeSource(
            mapOf(
                pdf.toString() to StatementFile("UBS statement.pdf", StatementFileKind.PDF, TestStatements.ubsAccount(today)),
                csv.toString() to StatementFile("export.csv", StatementFileKind.CSV, TestStatements.bankCsv(today)),
                otherPdf.toString() to StatementFile("other.pdf", StatementFileKind.PDF, "Some other bank\n01.01.26 Coffee 4.50"),
                swisscardPdf.toString() to StatementFile("c0ffee.pdf", StatementFileKind.PDF, TestStatements.swisscard(today)),
                ubsCardPdf.toString() to StatementFile("invoice.pdf", StatementFileKind.PDF, TestStatements.ubsCard(today)),
                stockPlanPdf.toString() to StatementFile("Quarterly Statement.pdf", StatementFileKind.PDF, TestStatements.morganStanley(today)),
                zerodhaXlsx.toString() to StatementFile("holdings-AB0000.xlsx", StatementFileKind.XLSX, TestStatements.zerodhaHoldings()),
            ),
        ),
        expenses, accounts, catalog, currencies, shares,
    ).cancelledAfterTest()

    @Test
    fun pdfRowsGetSensibleDefaults() {
        addAccount("Card", typeSeedKey = "credit_card", countrySeedKey = null) // a liability shouldn't be guessed
        val salary = addAccount("Salary account")
        val vm = viewModel()
        vm.load(pdf)
        // The guessed account arrives a moment after the rows.
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == salary }
        assertEquals(4, state.rows.size)
        val rows = state.rows.associateBy { it.description.substringBefore(" ·") }
        assertTrue(rows.getValue("EXAMPLE PROPERTIES AG").include)
        assertEquals(categoryId("utilities"), rows.getValue("SWISSCOM (SCHWEIZ) AG").categoryId)
        // Money in is imported too: the salary rule makes it income.
        val pay = rows.getValue("EXAMPLE EMPLOYER GMBH")
        assertTrue(pay.moneyIn && pay.include)
        assertEquals(categoryId("salary"), pay.categoryId)
        // Paying the card bill: imported, but in a category that isn't spending (the
        // card statement's purchases are), so it's listed without being counted twice.
        val cardBill = rows.getValue("UBS SWITZERLAND AG")
        assertTrue(!cardBill.skippedByRule && cardBill.include)
        assertEquals(categoryId(DefaultData.cardPaymentsCategory.key), cardBill.categoryId)
        assertEquals(4, state.includedCount)
        assertEquals("CHF 3’284.45", state.includedTotalText.withPlainSpaces()) // money out
        assertEquals("CHF 7’500.00", state.includedInText!!.withPlainSpaces()) // money in
        assertTrue(state.closingBalanceText != null && state.recordClosingBalance)
    }

    @Test
    fun importSavesExpensesAndClosingBalanceAndBlocksDuplicates() {
        val salary = addAccount("Salary account", balanceMinor = 1_00, date = today.minusMonths(2))
        val vm = viewModel()
        vm.load(pdf)
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == salary }
        val rent = state.rows.first { it.description.startsWith("EXAMPLE PROPERTIES") }
        vm.setCategory(rent.index, categoryId("housing"))
        vm.import()
        assertEquals(4, vm.uiState.await { it.importedCount != null }.importedCount)

        val saved = runBlocking { expenses.expensesBetween(today.minusMonths(2), today).first() }
        // The salary is stored like a refund (money in negative), in the Salary category.
        assertEquals(setOf(2_000_00L, 49_90L, 1_234_55L, -7_500_00L), saved.map { it.amountMinor }.toSet())
        assertEquals(categoryId("salary"), saved.single { it.amountMinor < 0 }.categoryId)
        val savedRent = saved.single { it.amountMinor == 2_000_00L }
        assertEquals(categoryId("housing"), savedRent.categoryId)
        assertTrue(savedRent.categoryLocked) // chosen by hand on the review screen
        assertEquals(salary, savedRent.accountId)
        assertEquals(1_421_555L, runBlocking { accounts.get(salary)!!.balanceMinor }) // closing balance recorded

        val again = viewModel()
        again.load(pdf)
        val second = again.uiState.await { s -> s.stage == ImportStage.REVIEW && s.rows.count { it.isDuplicate } == 4 }
        assertEquals(0, second.includedCount)
    }

    @Test
    fun reimportingAStatementAddsOnlyTheRowsLeftOutBefore() {
        // Imported once with the card bill left out (as the old "don't import" rule did).
        val salary = addAccount("Salary account", balanceMinor = 1_00, date = today.minusMonths(2))
        val first = viewModel()
        first.load(pdf)
        val state = first.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == salary }
        first.setInclude(state.rows.single { it.description.startsWith("UBS SWITZERLAND") }.index, false)
        first.import()
        assertEquals(3, first.uiState.await { it.importedCount != null }.importedCount)

        // Again: only the card bill is new, and it's ticked without touching anything.
        val again = viewModel()
        again.load(pdf)
        val second = again.uiState.await { s -> s.stage == ImportStage.REVIEW && s.rows.count { it.isDuplicate } == 3 }
        assertEquals(listOf("UBS SWITZERLAND AG"), second.rows.filter { it.include }.map { it.description.substringBefore(" ·") })
        again.import()
        assertEquals(1, again.uiState.await { it.importedCount != null }.importedCount)
    }

    @Test
    fun aRowDeletedAfterImportingStaysOutUntilTickedAgain() {
        val salary = addAccount("Salary account", balanceMinor = 1_00, date = today.minusMonths(2))
        val first = viewModel()
        first.load(pdf)
        first.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == salary }
        first.import()
        first.uiState.await { it.importedCount != null }
        val rent = runBlocking { expenses.expensesBetween(today.minusMonths(2), today).first() }.single { it.amountMinor == 2_000_00L }
        runBlocking { expenses.delete(rent.id) }

        val again = viewModel()
        again.load(pdf)
        val state = again.uiState.await { s -> s.stage == ImportStage.REVIEW && s.rows.any { it.wasDeleted } }
        val row = state.rows.single { it.wasDeleted }
        assertTrue(row.description.startsWith("EXAMPLE PROPERTIES") && !row.include)
        assertEquals(0, state.includedCount) // the rest was imported before

        // Ticked on purpose: imported again.
        again.setInclude(row.index, true)
        again.uiState.await { it.includedCount == 1 }
        again.import()
        assertEquals(1, again.uiState.await { it.importedCount != null }.importedCount)
    }

    @Test
    fun aRowWithTheSameDayAndAmountAlreadySavedIsAPossibleDuplicate() {
        val salary = addAccount("Salary account", balanceMinor = 1_00, date = today.minusMonths(2))
        // The phone bill, saved before from another file (different text), on the same account.
        runBlocking { expenses.save(expense("Swisscom e-bill", 49_90, date = today.minusDays(25), accountId = salary)) }
        val vm = viewModel()
        vm.load(pdf)
        val state = vm.uiState.await { s -> s.stage == ImportStage.REVIEW && s.accountId == salary && s.rows.any { it.possibleDuplicate } }
        val phone = state.rows.single { it.possibleDuplicate }
        assertTrue(phone.description.startsWith("SWISSCOM") && !phone.include)
        assertEquals(3, state.includedCount) // rent, card bill, salary
    }

    @Test
    fun csvIsReadWithCurrencyAndMapping() {
        addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val chf = addAccount("Salary account")
        val vm = viewModel()
        vm.load(csv)
        // CHF from the file beats the INR account.
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == chf }
        assertEquals(3, state.rows.size)
        assertEquals(3, state.includedCount) // the refund (money in) too
        assertEquals(categoryId("transport"), state.rows.single { it.description.startsWith("SBB") }.categoryId)

        // Changing the mapping re-reads the rows.
        vm.updateMapping { it.copy(descriptionColumns = listOf(it.descriptionColumns.last())) }
        assertTrue(vm.uiState.await { s -> s.rows.all { it.description.isBlank() } }.rows.isNotEmpty())
    }

    @Test
    fun choicesAndErrors() {
        addAccount("Salary account")
        val vm = viewModel()
        vm.load(pdf)
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId != null }
        val salary = state.rows.first { it.moneyIn }
        vm.setInclude(salary.index, true)
        vm.setRecordClosingBalance(false)
        vm.selectAccount(null)
        val updated = vm.uiState.await { it.accountId == null }
        assertEquals(4, updated.includedCount) // rent, phone, card bill + the salary ticked by hand
        assertFalse(updated.recordClosingBalance)

        val failing = viewModel()
        failing.load(broken)
        assertEquals(ImportError.UNREADABLE, failing.uiState.await { it.stage == ImportStage.ERROR }.error)
        val unknown = viewModel()
        unknown.load(otherPdf)
        assertEquals(ImportError.UNKNOWN_PDF, unknown.uiState.await { it.stage == ImportStage.ERROR }.error)
        assertNull(unknown.uiState.value.importedCount)
    }

    @Test
    fun cardStatementGoesToTheMatchingCardAndRecordsWhatIsOwed() {
        addAccount("Salary account", institution = "UBS")
        // Both banks appear in a Swisscard statement (it's paid into a UBS account); the issuer comes first.
        // Names chosen so the UBS card is listed first.
        val ubsCard = addAccount("Alpha Visa", typeSeedKey = "credit_card", countrySeedKey = null, institution = "UBS")
        val swisscard = addAccount("Zeta Amex", typeSeedKey = "credit_card", countrySeedKey = null, institution = "Swisscard")

        val vm = viewModel()
        vm.load(swisscardPdf)
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == swisscard }
        assertEquals(9, state.rows.size)
        assertFalse(state.rows.single { it.description.startsWith("YOUR PAYMENT") }.include) // paid from the bank
        assertEquals(7, state.includedCount) // purchases; the payment and the refund are money in
        vm.import()
        vm.uiState.await { it.importedCount != null }
        assertEquals(1_356_95L, runBlocking { accounts.get(swisscard)!!.balanceMinor }) // owed, as a positive number

        val ubs = viewModel()
        ubs.load(ubsCardPdf)
        ubs.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == ubsCard }
    }

    @Test
    fun stockPlanStatementSavesSharesCashAndPrice() = runBlocking {
        addAccount("Salary account")
        val type = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", type.id, "USD", null, 0, java.time.Instant.EPOCH, "Morgan Stanley", null, shareSymbol = "GOOG", units = BigDecimal.ZERO),
            balanceDate = today.minusMonths(6),
            recordBalance = true,
        )
        val stockPlan = accounts.accounts.first().single { it.shareSymbol != null }.id

        val vm = viewModel()
        vm.load(stockPlanPdf)
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == stockPlan }
        assertEquals(listOf(stockPlan), state.accounts.map { it.id }) // only accounts holding shares
        val holdings = state.holdings!!
        assertTrue(holdings.addsUp)
        assertEquals(today, holdings.date)
        assertTrue(holdings.sharesText, holdings.sharesText.startsWith("112.5 × "))
        vm.import()
        assertTrue(vm.uiState.await { it.importedCount != null }.holdingsSaved)

        val account = accounts.get(stockPlan)!!
        assertEquals(0, BigDecimal("112.5").compareTo(account.units))
        assertEquals(25_50L, account.balanceMinor)
        val price = shares.prices.first().pointAt("GOOG", today)!!
        assertEquals(0, BigDecimal("160").compareTo(price.price))
        assertTrue(price.fetched) // from a statement, so a price the user typed would still win
    }

    @Test
    fun aHoldingsFileSavesItsValueAsTheBalance() = runBlocking {
        addAccount("Salary account")
        val zerodha = addAccount("Zerodha", typeSeedKey = "in_stocks", currency = "INR", countrySeedKey = "in", institution = "Zerodha")
        val vm = viewModel()
        vm.load(zerodhaXlsx)
        val state = vm.uiState.await { it.stage == ImportStage.REVIEW && it.accountId == zerodha }
        assertTrue(state.rows.isEmpty())
        assertTrue(state.balanceOnly)
        assertFalse(state.valueNeedsCheck)
        vm.import()
        assertTrue(vm.uiState.await { it.importedCount != null }.balanceSaved)
        val entry = accounts.observeHistory(zerodha).first().first { it.date == java.time.LocalDate.of(2026, 3, 31) }
        assertEquals(13_650_00L, entry.balanceMinor)
    }
}
