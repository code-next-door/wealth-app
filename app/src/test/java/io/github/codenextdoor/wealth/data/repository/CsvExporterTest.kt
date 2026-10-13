package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.export.CsvExporter
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.Pension
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The CSV files are built from what's saved: every transaction, every balance entry. */
@RunWith(AndroidJUnit4::class)
class CsvExporterTest : DatabaseTest() {

    private val loans by lazy { LoanRepository(db) }
    private val pensions by lazy { PensionRepository(db) }
    private val houses by lazy { HouseRepository(db, accounts) }
    private val exporter by lazy { CsvExporter(expenses, catalog, accounts, currencies, loans.loans, pensions.pensions, houses.houses) { today } }

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
    fun anEmiIsSplitLikeInTheAppInterestCountedPrincipalNot() = runBlocking {
        // 100,000.00 at 12 %: the first EMI's interest is 1,000.00; the EMI is 3,000.00.
        val firstEmi = today.minusMonths(1).withDayOfMonth(5)
        catalog.addExpenseCategory("Home loan EMI")
        val category = catalog.expenseCategories.first().single { it.name == "Home loan EMI" }.id
        val loan = addAccount("Mortgage", typeSeedKey = "mortgage", balanceMinor = 100_000_00, date = firstEmi.minusDays(1))
        loans.save(Loan(loan, 100_000_00, firstEmi, 3_000_00, BigDecimal("12"), emiCategoryId = category))
        expenses.save(expense("HOME LOAN EMI", 3_000_00, date = firstEmi, categoryId = category))

        val lines = exporter.transactionsCsv().split("\r\n").filter { it.contains("HOME LOAN EMI") }
        assertEquals(
            listOf(
                "$firstEmi,HOME LOAN EMI,-1000.00,CHF,Home loan EMI,Spending,,,-3000.00",
                "$firstEmi,HOME LOAN EMI,-2000.00,CHF,Home loan EMI,Not counted,,,-3000.00",
            ),
            lines,
        )
    }

    @Test
    fun aHousesMonthEndsAreCalculatedRowsItsPurchaseIsReal() = runBlocking {
        val bought = today.minusMonths(3)
        houses.save(HouseDetails(0, "Flat", "CHF", null, 500_000_00, bought, BigDecimal("10"), null))
        val rows = exporter.balancesCsv().split("\r\n").filter { it.contains(",Flat,") }
        assertTrue(rows.toString(), rows.any { it.startsWith("$bought,Flat,Asset,") && it.endsWith(",500000.00,,") })
        val monthEnds = rows.filter { it.endsWith(",yes") }
        assertTrue(rows.toString(), monthEnds.size >= 2)
        monthEnds.map { LocalDate.parse(it.substringBefore(",")) }.forEach {
            assertTrue(it.toString(), it.isAfter(bought) && !it.isAfter(today) && it == YearMonth.from(it).atEndOfMonth())
        }
    }

    @Test
    fun balancesWithAccountTypeAndDebtsNegative() {
        addAccount("Savings", balanceMinor = 1_000_00, date = today.minusDays(10))
        addAccount("Visa", typeSeedKey = "credit_card", balanceMinor = 250_00, date = today)
        val lines = runBlocking { exporter.balancesCsv() }.split("\r\n").filter { it.isNotBlank() }
        assertTrue(lines.toString(), lines.any { it.startsWith("${today.minusDays(10)},Savings,Asset,") && it.endsWith(",CHF,1000.00,,") })
        assertTrue(lines.toString(), lines.any { it.startsWith("$today,Visa,Liability,") && it.endsWith(",CHF,-250.00,,") })
    }

    @Test
    fun calculatedBalancesAreMarkedLikeInTheHistory() = runBlocking {
        // A saved estimate (a longer statement's month-end).
        val funds = addAccount("Funds", balanceMinor = 1_000_00, date = today.minusMonths(4))
        val estimateDay = today.minusMonths(2).withDayOfMonth(1).minusDays(1)
        accounts.addHistoryEntry(funds, estimateDay, 1_100_00, estimated = true)
        // A calculated loan and a growing pension: their month-ends, worked out like the account's history shows them.
        val firstEmi = today.minusMonths(3)
        val loan = addAccount("Mortgage", typeSeedKey = "mortgage", balanceMinor = 100_000_00, date = firstEmi.minusDays(1))
        loans.save(Loan(loan, 100_000_00, firstEmi, 1_000_00, BigDecimal("2")))
        val pension = addAccount("Pillar 2", typeSeedKey = "ch_pillar2", balanceMinor = 50_000_00, date = today.minusMonths(3))
        pensions.save(Pension(pension, 12_000_00, BigDecimal("1")))

        val lines = exporter.balancesCsv().split("\r\n").filter { it.isNotBlank() }
        assertTrue(lines.toString(), lines.any { it.startsWith("$estimateDay,Funds,") && it.endsWith(",yes") })
        assertTrue(lines.toString(), lines.any { it.startsWith("${today.minusMonths(4)},Funds,") && it.endsWith(",") })
        val loanEnds = lines.filter { it.contains(",Mortgage,Liability,") && it.endsWith(",yes") }
        val pensionEnds = lines.filter { it.contains(",Pillar 2,Asset,") && it.endsWith(",yes") }
        assertTrue(lines.toString(), loanEnds.size >= 2 && pensionEnds.size >= 2)
        // Up to today only, and only month-ends.
        (loanEnds + pensionEnds).map { LocalDate.parse(it.substringBefore(",")) }.forEach {
            assertTrue(it.toString(), !it.isAfter(today) && it == YearMonth.from(it).atEndOfMonth())
        }
    }
}
