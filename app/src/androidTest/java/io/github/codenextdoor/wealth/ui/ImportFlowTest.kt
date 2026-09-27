package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Statement import through the real UI, with the file picker stubbed. Invented statements only. */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest : UiTest() {

    private val files = mutableListOf<File>()

    @After
    fun deleteFiles() {
        files.forEach { it.delete() }
    }

    private fun expensesMatching(text: String): List<Expense> = runBlocking {
        container.expenseRepository.expensesBetween(LocalDate.now().minusYears(2), LocalDate.now().plusYears(1)).first()
            .filter { it.description.contains(text) }
    }

    private fun startImport() {
        openTab("Spending")
        rule.onNodeWithContentDescription("Import statement").performClick()
    }

    @Test
    fun pdfStatementImportsSpendingOnly() {
        addBankAccount("Import PDF account")
        val pdf = cacheFile("fake-ubs-statement.pdf").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open("fake-ubs-statement.pdf")
            .use { input -> pdf.outputStream().use { input.copyTo(it) } }
        stubOpenDocument(pdf)

        startImport()
        waitForText("UBS account statement", substring = true)
        // Rent and telecom are spending; the salary (money in) starts unticked.
        waitForText("Import 2 expenses")
        assertTrue(isShown("Money in", substring = true))

        rule.onNodeWithText("Import 2 expenses").performClick()
        waitForText("Imported 2 expenses")
        assertEquals(1, expensesMatching("EXAMPLE PROPERTIES AG").size)
        assertEquals(1, expensesMatching("EXAMPLE TELECOM AG").size)
        assertTrue(expensesMatching("EXAMPLE EMPLOYER").isEmpty())
    }

    @Test
    fun csvExportImportsOnceAndDetectsDuplicates() {
        addBankAccount("Import CSV account")
        val today = LocalDate.now()
        val tag = "UITEST${System.nanoTime() % 100000}"
        val csv = cacheFile("export.csv").also { files += it }
        csv.writeText(
            """
            Valued in:;CHF;

            Booking date;Description1;Debit;Credit;Balance
            $today;"COOP $tag";-45.30;;900.00
            ${today.minusDays(1)};"SBB CFF FFS $tag";-88.00;;945.30
            ${today.minusDays(2)};"REFUND $tag";;20.00;1033.30
            """.trimIndent(),
        )
        stubOpenDocument(csv)

        startImport()
        waitForText("Import 2 expenses") // the refund (money in) starts unticked
        rule.onNodeWithText("Import 2 expenses").performClick()
        waitForText("Imported 2 expenses")
        assertEquals(2, expensesMatching(tag).size)

        // The same file again: every row is recognized, nothing to import.
        startImport()
        waitForText("Import 0 expenses")
        assertTrue(isShown("Already imported", substring = true))
        assertEquals(2, expensesMatching(tag).size)
    }

    @Test
    fun unknownPdfLayoutExplainsWhatToDo() {
        val pdf = cacheFile("not-a-statement.pdf").also { files += it }
        // A valid PDF with unrelated text.
        InstrumentationRegistry.getInstrumentation().context.assets.open("fake-ubs-statement.pdf")
            .use { input -> pdf.outputStream().use { out -> out.write(input.readBytes().toString(Charsets.ISO_8859_1).replace("UBS", "XYZ").toByteArray(Charsets.ISO_8859_1)) } }
        stubOpenDocument(pdf)
        startImport()
        waitForText("layout isn't supported yet", substring = true)
    }
}
