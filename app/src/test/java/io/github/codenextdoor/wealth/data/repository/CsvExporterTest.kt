package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.export.CsvExporter
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The CSV files are built from what's saved: every transaction, every balance entry. */
@RunWith(AndroidJUnit4::class)
class CsvExporterTest : DatabaseTest() {

    private val exporter by lazy { CsvExporter(expenses, catalog, accounts, currencies) }

    @Test
    fun transactionsFromEveryMonthWithTheirNames() {
        val card = addAccount("Everyday card")
        runBlocking {
            expenses.save(expense("COOP ZURICH", 45_30, date = today.minusYears(2), categoryId = categoryId("groceries"), accountId = card))
            expenses.save(expense("REFUND", -3_00, date = today))
        }
        val lines = runBlocking { exporter.transactionsCsv() }.split("\r\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertEquals("${today.minusYears(2)},COOP ZURICH,-45.30,CHF,Groceries,Spending,Everyday card,,", lines[1])
        assertTrue(lines[2], lines[2].startsWith("$today,REFUND,3.00,CHF,,Income,"))
    }

    @Test
    fun balancesWithAccountTypeAndDebtsNegative() {
        addAccount("Savings", balanceMinor = 1_000_00, date = today.minusDays(10))
        addAccount("Visa", typeSeedKey = "credit_card", balanceMinor = 250_00, date = today)
        val lines = runBlocking { exporter.balancesCsv() }.split("\r\n").filter { it.isNotBlank() }
        assertTrue(lines.toString(), lines.any { it.startsWith("${today.minusDays(10)},Savings,Asset,") && it.endsWith(",CHF,1000.00,") })
        assertTrue(lines.toString(), lines.any { it.startsWith("$today,Visa,Liability,") && it.endsWith(",CHF,-250.00,") })
    }
}
