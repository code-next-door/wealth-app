package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CashFlowTest {

    private val day = LocalDate.of(2026, 9, 1)
    private val rates = RateBook(listOf(RatePoint("CHF", "INR", BigDecimal("100"), day)))

    private val groceries = ExpenseCategory(1, "Groceries")
    private val salary = ExpenseCategory(2, "Salary", isIncome = true)
    private val transfers = ExpenseCategory(3, "Transfers", countsAsSpending = false)
    private val categories = listOf(groceries, salary, transfers).associateBy { it.id }

    private fun row(description: String, amountMinor: Long, category: ExpenseCategory?, currency: String = "CHF") =
        Expense(0, day, amountMinor, currency, description, category?.id, false, null, null)

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    private fun flow(vararg rows: Expense) = CashFlow.of(rows.toList(), categories, rates, "CHF") { 2 }

    @Test
    fun everyRowCountsWhereTheTableSays() {
        val flow = flow(
            row("Groceries", 100_00, groceries), // money out, spending category: spending
            row("Mystery", 20_00, null), // money out, no category: spending
            row("Refund", -10_00, groceries), // money in, spending category: lowers its spending
            row("Pay", -5_000_00, salary), // money in, income category: income
            row("Found", -50_00, null), // money in, no category: income
            row("Correction", 100_00, salary), // money out, income category: lowers income
            row("To broker", 1_000_00, transfers), // not counted
            row("From broker", -300_00, transfers), // not counted, either way
        )
        assertEquals(listOf("Groceries", "Mystery", "Refund"), flow.spending.map { it.description })
        assertEquals(listOf("Pay", "Found", "Correction"), flow.income.map { it.description })
        assertEquals(listOf("To broker", "From broker"), flow.notCounted.map { it.description })
        assertAmount("110", flow.spendingSummary.total) // 100 + 20 - 10
        assertAmount("90", flow.spendingSummary.byCategory[groceries.id])
        assertAmount("4950", flow.incomeTotal) // 5000 + 50 - 100
        assertAmount("4840", flow.saved)
        assertAmount("97.78", flow.savedPercent) // 4840 / 4950, two decimals
    }

    @Test
    fun incomeInAnotherCurrencyIsConvertedOnItsDay() {
        val flow = flow(row("Rent paid to me", -10_000_00, salary, currency = "INR"), row("Coffee", 5_00, groceries))
        assertAmount("100", flow.incomeTotal)
        assertAmount("95", flow.saved)
    }

    @Test
    fun withoutIncomeThereIsNoSavedPercent() {
        val flow = flow(row("Groceries", 100_00, groceries))
        assertAmount("0", flow.incomeTotal)
        assertAmount("-100", flow.saved)
        assertNull(flow.savedPercent)
    }

    @Test
    fun anIncomeCategorySwitchedOffStillCountsAsIncome() {
        // The switch is for spending categories; income categories don't show it.
        val odd = ExpenseCategory(4, "Bonus", countsAsSpending = false, isIncome = true)
        val flow = CashFlow.of(listOf(row("Bonus", -1_000_00, odd)), mapOf(odd.id to odd), rates, "CHF") { 2 }
        assertAmount("1000", flow.incomeTotal)
    }
}
