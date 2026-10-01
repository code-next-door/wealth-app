package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun flow(vararg rows: Expense) = CashFlow.of(rows.toList(), categories, rates, "CHF", { 2 })

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
        val flow = CashFlow.of(listOf(row("Bonus", -1_000_00, odd)), mapOf(odd.id to odd), rates, "CHF", { 2 })
        assertAmount("1000", flow.incomeTotal)
    }

    private val homeLoan = ExpenseCategory(5, "Home loan")
    private val withLoan = categories + (homeLoan.id to homeLoan)

    /** This month's EMI interest for "Home loan": ₹33,000 (in CHF here, for simple numbers: 330). */
    private val interest = { categoryId: Long, month: java.time.YearMonth ->
        if (categoryId == homeLoan.id && month == java.time.YearMonth.from(day)) 330_00L to "CHF" else null
    }

    private fun splitFlow(vararg rows: Expense) = CashFlow.of(rows.toList(), withLoan, rates, "CHF", { 2 }, interest)

    @Test
    fun anEmiCountsOnlyItsInterestAndTheRestIsPrincipalRepaid() {
        val emi = row("EMI", 1_000_00, homeLoan).copy(id = 1)
        val flow = splitFlow(emi, row("Groceries", 100_00, groceries))
        assertAmount("430", flow.spendingSummary.total) // 330 interest + 100 groceries
        assertAmount("330", flow.spendingSummary.byCategory[homeLoan.id])
        val split = flow.splits.getValue(1)
        assertEquals(330_00L, split.interestMinor)
        assertEquals(670_00L, split.principalMinor)
        // The principal part shows with what isn't counted.
        assertEquals(listOf(670_00L), flow.notCounted.map { it.amountMinor })
        assertEquals(listOf("EMI", "Groceries"), flow.spending.map { it.description }) // the EMI stays one row
    }

    @Test
    fun anExtraPrepaymentInTheSameMonthIsAllPrincipal() {
        val emi = row("EMI", 1_000_00, homeLoan).copy(id = 1)
        val prepayment = row("Prepayment", 5_000_00, homeLoan).copy(id = 2, date = day.plusDays(10))
        val flow = splitFlow(emi, prepayment)
        assertAmount("330", flow.spendingSummary.total) // the month's interest counts once
        assertEquals(5_000_00L, flow.splits.getValue(2).principalMinor)
        assertEquals(0L, flow.splits.getValue(2).interestMinor)
    }

    @Test
    fun aCategoryWithoutALinkedLoanIsUnchanged() {
        val flow = splitFlow(row("Groceries", 100_00, groceries))
        assertAmount("100", flow.spendingSummary.total)
        assertTrue(flow.splits.isEmpty())
    }

    @Test
    fun aSplitExpenseCountsAsItsPartsEachInItsOwnCategory() {
        // A 120.00 supermarket bill: 20.00 of it moved to the broker category (not counted),
        // 30.00 to no category; the rest (70.00) stays groceries.
        val bill = row("Migros", 120_00, groceries).copy(
            note = "weekly shop",
            parts = listOf(ExpensePart(amountMinor = 20_00, categoryId = transfers.id, note = "gift card"), ExpensePart(amountMinor = 30_00, categoryId = null)),
        )
        val flow = flow(bill)
        assertEquals(listOf(70_00L, 30_00L), flow.spending.map { it.amountMinor })
        assertEquals(listOf(20_00L), flow.notCounted.map { it.amountMinor })
        assertAmount("100", flow.spendingSummary.total)
        assertAmount("70", flow.spendingSummary.byCategory[groceries.id])
        // Each part keeps the expense's text and id (it opens the expense) and says what it's part of.
        assertTrue((flow.spending + flow.notCounted).all { it.description == "Migros" && it.id == bill.id && it.partOfMinor == 120_00L })
        assertEquals("gift card", flow.notCounted.single().note)
        assertEquals("weekly shop", flow.spending.last().note) // no note of its own: the expense's
    }

    @Test
    fun aSplitRefundKeepsItsSignInEveryPart() {
        val refund = row("Returned", -50_00, groceries).copy(parts = listOf(ExpensePart(amountMinor = -10_00, categoryId = transfers.id)))
        val flow = flow(refund)
        assertAmount("-40", flow.spendingSummary.total)
        assertEquals(listOf(-10_00L), flow.notCounted.map { it.amountMinor })
    }
}
