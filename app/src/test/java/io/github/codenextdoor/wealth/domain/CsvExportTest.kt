package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CsvExportTest {

    private val day = LocalDate.of(2026, 3, 14)
    private val groceries = ExpenseCategory(1, "Groceries")
    private val salary = ExpenseCategory(2, "Salary", isIncome = true)
    private val transfers = ExpenseCategory(3, "Investments & transfers", countsAsSpending = false)
    private val categories = listOf(groceries, salary, transfers).associateBy { it.id }

    private fun expense(description: String, amountMinor: Long, categoryId: Long? = null, note: String? = null, parts: List<ExpensePart> = emptyList(), date: LocalDate = day, currency: String = "CHF") =
        Expense(0, date, amountMinor, currency, description, categoryId, false, 7, note, parts = parts)

    private fun transactionsCsv(vararg expenses: Expense) = CsvExport.transactions(
        CsvExport.transactionRows(expenses.toList(), categories, accountNames = mapOf(7L to "Everyday card"), decimalsOf = { code -> if (code == "JPY") 0 else 2 }),
    )

    private fun lines(csv: String) = csv.removePrefix(CsvExport.BOM).split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun startsWithAByteOrderMarkSoSpreadsheetsReadUtf8() {
        assertTrue(transactionsCsv().startsWith("\uFEFF"))
    }

    @Test
    fun transactionsHaveAHeaderAndMoneyOutIsNegative() {
        val csv = lines(transactionsCsv(expense("COOP ZURICH", 45_30, groceries.id), expense("SALARY", -5_000_00, salary.id)))
        assertEquals("Date,Description,Amount,Currency,Category,Counts as,Account,Note,Split of", csv[0])
        assertEquals("2026-03-14,COOP ZURICH,-45.30,CHF,Groceries,Spending,Everyday card,,", csv[1])
        assertEquals("2026-03-14,SALARY,5000.00,CHF,Salary,Income,Everyday card,,", csv[2])
    }

    @Test
    fun oldestFirst() {
        val csv = lines(transactionsCsv(expense("NEW", 1_00, date = day), expense("OLD", 1_00, date = day.minusDays(30))))
        assertTrue(csv[1].contains("OLD"))
        assertTrue(csv[2].contains("NEW"))
    }

    @Test
    fun whatCountsHowIsSaid() {
        val csv = lines(transactionsCsv(expense("BROKER", 500_00, transfers.id), expense("REFUND", -3_00, groceries.id), expense("MYSTERY IN", -9_00)))
        assertTrue(csv[1], csv[1].contains(",Not counted,"))
        assertTrue(csv[2], csv[2].contains(",3.00,CHF,Groceries,Spending,")) // a refund: money in, under spending
        assertTrue(csv[3], csv[3].contains(",9.00,CHF,,Income,")) // money in without a category counts as income
    }

    @Test
    fun aSplitBillIsOneRowPerPartWithTheWholeAmount() {
        val bill = expense("SUPERMARKET", 120_00, groceries.id, parts = listOf(ExpensePart(amountMinor = 20_00, categoryId = transfers.id, note = "gift card")))
        val csv = lines(transactionsCsv(bill))
        assertEquals(3, csv.size)
        assertEquals("2026-03-14,SUPERMARKET,-100.00,CHF,Groceries,Spending,Everyday card,,-120.00", csv[1])
        assertEquals("2026-03-14,SUPERMARKET,-20.00,CHF,Investments & transfers,Not counted,Everyday card,gift card,-120.00", csv[2])
    }

    @Test
    fun textWithCommasQuotesOrLineBreaksIsQuoted() {
        val csv = transactionsCsv(expense("SHOP, \"BEST\" PRICES", 1_00, note = "line one\nline two"))
        assertTrue(csv, csv.contains("\"SHOP, \"\"BEST\"\" PRICES\""))
        assertTrue(csv, csv.contains("\"line one\nline two\""))
    }

    @Test
    fun textThatASpreadsheetWouldRunAsAFormulaIsDefused() {
        val csv = lines(transactionsCsv(expense("=HYPERLINK(\"x\")", 1_00, note = "+41 79 000 00 00"), expense("-SBB", 1_00), expense("@HOME", 1_00)))
        assertTrue(csv[1], csv[1].contains(",\"'=HYPERLINK(\"\"x\"\")\","))
        assertTrue(csv[1], csv[1].contains(",'+41 79 000 00 00,"))
        assertTrue(csv[2], csv[2].contains(",'-SBB,"))
        assertTrue(csv[3], csv[3].contains(",'@HOME,"))
        // Amounts stay numbers.
        assertTrue(csv[2], csv[2].contains(",-1.00,"))
    }

    @Test
    fun currenciesWithoutCentsHaveNoDecimals() {
        assertTrue(lines(transactionsCsv(expense("TOKYO", 1500, currency = "JPY")))[1].contains(",-1500,JPY,"))
    }

    @Test
    fun balancesListEveryEntryWithDebtsNegativeAndShares() {
        val csv = lines(
            CsvExport.balances(
                listOf(
                    CsvExport.BalanceRow(day, "Mortgage", liability = true, type = "Mortgage", currencyCode = "CHF", balanceMinor = 500_000_00, decimals = 2, calculated = true),
                    CsvExport.BalanceRow(day.minusYears(1), "Savings", liability = false, type = "Bank account", currencyCode = "CHF", balanceMinor = 12_345_60, decimals = 2),
                    CsvExport.BalanceRow(day, "Stock plan", liability = false, type = "Shares (stock plan)", currencyCode = "USD", balanceMinor = 767_62, decimals = 2, units = BigDecimal("1455.900")),
                ),
            ),
        )
        assertEquals("Date,Account,Asset or liability,Account type,Currency,Balance,Shares,Calculated", csv[0])
        assertEquals("2025-03-14,Savings,Asset,Bank account,CHF,12345.60,,", csv[1])
        assertEquals("2026-03-14,Mortgage,Liability,Mortgage,CHF,-500000.00,,yes", csv[2])
        assertEquals("2026-03-14,Stock plan,Asset,Shares (stock plan),USD,767.62,1455.900,", csv[3])
    }
}
