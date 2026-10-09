package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Settings › Backup › Export as CSV writes the picked file. */
@RunWith(AndroidJUnit4::class)
class ExportFlowTest : UiTest() {

    private val files = mutableListOf<java.io.File>()

    @After
    fun deleteFiles() = files.forEach { it.delete() }

    @Test
    fun transactionsAndBalancesAreSavedAsCsv() {
        val tag = "EXPORTTEST${System.nanoTime() % 100000}"
        runBlocking {
            container.expenseRepository.save(Expense(0, LocalDate.of(2025, 5, 2), 12_40, "CHF", "BAKERY $tag", null, false, null, null))
        }
        addBankAccount("Export $tag")
        rule.onNodeWithContentDescription("Settings").performClick()

        val transactions = cacheFile("export-test-transactions.csv").also { files += it }
        stubCreateDocument(transactions)
        rule.onNodeWithText("Export transactions (CSV)").performScrollTo().tap()
        waitForText("Transactions exported")
        val text = transactions.readText()
        assertTrue(text.startsWith("\uFEFFDate,Description,Amount,Currency"))
        assertTrue(text.contains("2025-05-02,BAKERY $tag,-12.40,CHF,"))

        val balances = cacheFile("export-test-balances.csv").also { files += it }
        stubCreateDocument(balances)
        rule.onNodeWithText("Export balance history (CSV)").performScrollTo().tap()
        // Its message waits behind the first one (snackbars show one at a time): check the file.
        rule.waitUntil(10_000) { balances.exists() && balances.readText().contains(",Export $tag,Asset,") }
    }
}
