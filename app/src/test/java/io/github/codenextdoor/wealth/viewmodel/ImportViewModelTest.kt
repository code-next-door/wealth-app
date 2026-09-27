package io.github.codenextdoor.wealth.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.imports.ImportError
import io.github.codenextdoor.wealth.imports.ImportStage
import io.github.codenextdoor.wealth.imports.ImportViewModel
import io.github.codenextdoor.wealth.imports.StatementFile
import io.github.codenextdoor.wealth.imports.StatementFileKind
import io.github.codenextdoor.wealth.imports.StatementSource
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.testutil.DatabaseTest
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

    private val pdf = Uri.parse("content://test/statement.pdf")
    private val csv = Uri.parse("content://test/export.csv")
    private val broken = Uri.parse("content://test/broken")
    private val otherPdf = Uri.parse("content://test/other.pdf")
    private val swisscardPdf = Uri.parse("content://test/swisscard.pdf")
    private val ubsCardPdf = Uri.parse("content://test/invoice.pdf")

    private fun viewModel() = ImportViewModel(
        AppLock(context, prefsName = "lock_import_test"),
        FakeSource(
            mapOf(
                pdf.toString() to StatementFile("UBS statement.pdf", StatementFileKind.PDF, TestStatements.ubsAccount(today)),
                csv.toString() to StatementFile("export.csv", StatementFileKind.CSV, TestStatements.bankCsv(today)),
                otherPdf.toString() to StatementFile("other.pdf", StatementFileKind.PDF, "Some other bank\n01.01.26 Coffee 4.50"),
                swisscardPdf.toString() to StatementFile("c0ffee.pdf", StatementFileKind.PDF, TestStatements.swisscard(today)),
                ubsCardPdf.toString() to StatementFile("invoice.pdf", StatementFileKind.PDF, TestStatements.ubsCard(today)),
            ),
        ),
        expenses, accounts, catalog, currencies,
    )

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
        assertFalse(rows.getValue("EXAMPLE EMPLOYER GMBH").include) // money in
        assertTrue(rows.getValue("EXAMPLE EMPLOYER GMBH").moneyIn)
        val cardBill = rows.getValue("UBS SWITZERLAND AG")
        assertTrue(cardBill.skippedByRule && !cardBill.include)
        assertEquals(2, state.includedCount)
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
        assertEquals(2, vm.uiState.await { it.importedCount != null }.importedCount)

        val saved = runBlocking { expenses.expensesBetween(today.minusMonths(2), today).first() }
        assertEquals(setOf(2_000_00L, 49_90L), saved.map { it.amountMinor }.toSet())
        val savedRent = saved.single { it.amountMinor == 2_000_00L }
        assertEquals(categoryId("housing"), savedRent.categoryId)
        assertTrue(savedRent.categoryLocked) // chosen by hand on the review screen
        assertEquals(salary, savedRent.accountId)
        assertEquals(1_421_555L, runBlocking { accounts.get(salary)!!.balanceMinor }) // closing balance recorded

        val again = viewModel()
        again.load(pdf)
        val second = again.uiState.await { s -> s.stage == ImportStage.REVIEW && s.rows.count { it.isDuplicate } == 2 }
        assertEquals(0, second.includedCount)
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
        assertEquals(2, state.includedCount) // refund starts unticked
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
        assertEquals(3, updated.includedCount)
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
}
