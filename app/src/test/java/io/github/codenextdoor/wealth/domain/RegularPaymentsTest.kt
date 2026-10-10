package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Regular payments: the same shop, the exact same amount, monthly (3+) or yearly (2+). */
class RegularPaymentsTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val categorizer = Categorizer(listOf(CategoryRule(1, "NETFLIX", 5), CategoryRule(2, "COOP", 6)))
    private val categories = mapOf(
        5L to ExpenseCategory(5, "Subscriptions"),
        6L to ExpenseCategory(6, "Groceries"),
        7L to ExpenseCategory(7, "Credit card payments", countsAsSpending = false),
        8L to ExpenseCategory(8, "Salary", isIncome = true),
    )
    private var nextId = 1L

    private fun charge(day: LocalDate, amountMinor: Long, description: String = "NETFLIX.COM 866-579", category: Long? = 5, currency: String = "CHF") =
        Expense(nextId++, day, amountMinor, currency, description, category, false, null, null)

    private fun monthly(first: LocalDate, times: Int, amountMinor: Long = 17_90, description: String = "NETFLIX.COM 866-579", category: Long? = 5) =
        (0 until times).map { charge(first.plusMonths(it.toLong()), amountMinor, description, category) }

    private fun find(expenses: List<Expense>, hidden: Set<String> = emptySet()) =
        RegularPayments.find(expenses, categories, categorizer, today, hidden)

    @Test
    fun threeMonthlyChargesOfTheSameAmountAreARegularPayment() {
        val found = find(monthly(LocalDate.of(2026, 7, 3), 4)).active.single()
        assertEquals("NETFLIX", found.shop)
        assertEquals(17_90L, found.amountMinor)
        assertEquals(RegularPayments.Cadence.MONTHLY, found.cadence)
        assertEquals(LocalDate.of(2026, 10, 3), found.lastDate)
        assertEquals(LocalDate.of(2026, 11, 3), found.nextDate)
        assertEquals(4, found.count)
        assertEquals(214_80L, found.yearlyMinor)
    }

    @Test
    fun twoChargesAreNotEnoughForMonthly() {
        assertTrue(find(monthly(LocalDate.of(2026, 8, 3), 2)).active.isEmpty())
    }

    @Test
    fun differentAmountsAreNeverMatchedSoGroceriesAreLeftOut() {
        val groceries = listOf(23_45L, 61_10L, 18_00L, 44_95L, 30_20L, 52_75L, 27_40L, 39_90L)
            .mapIndexed { i, amount -> charge(LocalDate.of(2026, 8, 1).plusDays(i * 7L), amount, "COOP-4521 ZURICH", 6) }
        assertTrue(find(groceries).active.isEmpty())
    }

    @Test
    fun theSameAmountWeeklyIsNotMonthly() {
        val weekly = (0 until 6).map { charge(LocalDate.of(2026, 8, 1).plusWeeks(it.toLong()), 5_00, "COOP-4521 ZURICH", 6) }
        assertTrue(find(weekly).active.isEmpty())
    }

    @Test
    fun aYearlyChargeNeedsTwoYears() {
        val prime = listOf(charge(LocalDate.of(2025, 3, 14), 99_00, "AMAZON PRIME*AB12", null), charge(LocalDate.of(2026, 3, 14), 99_00, "AMAZON PRIME*CD34", null))
        val found = find(prime).active.single()
        assertEquals(RegularPayments.Cadence.YEARLY, found.cadence)
        assertEquals("AMAZON", found.shop)
        assertEquals(LocalDate.of(2027, 3, 14), found.nextDate)
        assertEquals(99_00L, found.yearlyMinor)
        assertTrue(find(prime.take(1)).active.isEmpty())
    }

    @Test
    fun aPriceChangeStopsTheOldAmountAndTheNewOneNeedsThreeCharges() {
        val old = monthly(LocalDate.of(2026, 3, 3), 4, amountMinor = 15_90) // Mar–Jun
        val new = monthly(LocalDate.of(2026, 7, 3), 2, amountMinor = 17_90) // Jul–Aug
        val result = find(old + new)
        assertTrue(result.active.isEmpty())
        assertEquals(listOf(15_90L), result.stopped.map { it.amountMinor })
    }

    @Test
    fun noChargeForTwoCyclesIsStopped() {
        val result = find(monthly(LocalDate.of(2026, 4, 3), 4)) // last 3 Jul; 3 Sep passed too
        assertTrue(result.active.isEmpty())
        assertEquals(LocalDate.of(2026, 7, 3), result.stopped.single().lastDate)
        // One missed month is still active (the charge may just be late).
        assertEquals(1, find(monthly(LocalDate.of(2026, 6, 3), 4)).active.size) // last 3 Sep, 3 Oct missed
    }

    @Test
    fun anOlderIrregularChargeDoesNotHideTheRegularRun() {
        val stray = charge(LocalDate.of(2026, 1, 20), 17_90)
        assertEquals(3, find(listOf(stray) + monthly(LocalDate.of(2026, 8, 3), 3)).active.single().count)
    }

    @Test
    fun onlyCountedSpendingAndMoneyOut() {
        val cardBill = monthly(LocalDate.of(2026, 7, 28), 3, amountMinor = 1_200_00, description = "UBS CARD PAYMENT", category = 7)
        val salary = monthly(LocalDate.of(2026, 7, 25), 3, amountMinor = -8_000_00, description = "EMPLOYER AG", category = 8)
        assertTrue(find(cardBill + salary).active.isEmpty())
    }

    @Test
    fun hiddenShopsAreListedApart() {
        val result = find(monthly(LocalDate.of(2026, 8, 3), 3), hidden = setOf("NETFLIX"))
        assertTrue(result.active.isEmpty())
        assertEquals("NETFLIX", result.hidden.single().shop)
    }

    @Test
    fun theSameDayTwiceCountsOnce() {
        val charges = monthly(LocalDate.of(2026, 8, 3), 3) + charge(LocalDate.of(2026, 10, 3), 17_90)
        assertEquals(3, find(charges).active.single().count)
    }
}
