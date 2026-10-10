package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MonthlyReviewTest {

    private val september = YearMonth.of(2026, 9)
    private val rates = RateBook(listOf(RatePoint("CHF", "INR", BigDecimal("100"), LocalDate.of(2026, 1, 1))))
    private val groceries = ExpenseCategory(1, "Groceries")
    private val rent = ExpenseCategory(2, "Rent")
    private val salary = ExpenseCategory(3, "Salary", isIncome = true)
    private val categories = listOf(groceries, rent, salary).associateBy { it.id }

    private fun valued(id: Long, kind: AssetKind, vararg balances: Pair<LocalDate, Long>) = ValuedAccount(
        account = Account(id, "Account $id", id, "CHF", null, balances.last().second, Instant.EPOCH, null, null),
        kind = kind,
        decimals = 2,
        history = balances.map { (day, minor) -> BalanceEntry(0, id, day, minor) },
    )

    private fun spent(day: LocalDate, minor: Long, category: ExpenseCategory?) =
        Expense(0, day, minor, "CHF", "row", category?.id, false, null, null)

    private fun review(accounts: List<ValuedAccount>, expenses: List<Expense>, calculated: Set<Long> = emptySet()) = MonthlyReview.of(
        month = september,
        calculator = NetWorthCalculator(accounts, rates, "CHF"),
        expenses = expenses,
        categories = categories,
        rates = rates,
        baseCurrency = "CHF",
        decimalsOf = { 2 },
        calculated = calculated,
    )

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun netWorthFromTheEndOfTheMonthBeforeToTheEndOfTheMonth() {
        val savings = valued(1, AssetKind.ASSET, LocalDate.of(2026, 8, 31) to 10_000_00, LocalDate.of(2026, 9, 30) to 11_000_00)
        val card = valued(2, AssetKind.LIABILITY, LocalDate.of(2026, 8, 31) to 500_00, LocalDate.of(2026, 9, 30) to 1_000_00)
        val review = review(listOf(savings, card), emptyList())
        assertAmount("9500", review.startNetWorth)
        assertAmount("10000", review.endNetWorth)
        assertAmount("500", review.change)
        assertAmount("5.26", review.changePercent!!.setScale(2, java.math.RoundingMode.HALF_EVEN))
        // Biggest movers first, a debt growing shows as a fall.
        assertEquals(listOf(1L, 2L), review.movers.map { it.first.account.id })
        assertAmount("-500", review.movers[1].second)
    }

    @Test
    fun noPercentWhenTheMonthStartedAtZero() {
        val savings = valued(1, AssetKind.ASSET, LocalDate.of(2026, 9, 10) to 1_000_00)
        assertNull(review(listOf(savings), emptyList()).changePercent)
    }

    @Test
    fun incomeSpendingAndSavedAgainstTheMonthBefore() {
        val expenses = listOf(
            spent(LocalDate.of(2026, 9, 25), -5_000_00, salary),
            spent(LocalDate.of(2026, 9, 1), 2_000_00, rent),
            spent(LocalDate.of(2026, 9, 12), 500_00, groceries),
            spent(LocalDate.of(2026, 8, 25), -5_000_00, salary),
            spent(LocalDate.of(2026, 8, 1), 2_000_00, rent),
            spent(LocalDate.of(2026, 8, 12), 1_000_00, groceries),
            spent(LocalDate.of(2026, 10, 1), 9_999_00, groceries), // another month: ignored
        )
        val review = review(emptyList(), expenses)
        assertAmount("5000", review.income)
        assertAmount("2500", review.spending)
        assertAmount("2500", review.saved)
        assertAmount("50", review.savingsRate)
        assertAmount("3000", review.previousSpending)
        assertAmount("40", review.previousSavingsRate)
    }

    @Test
    fun topCategoriesLargestFirstWithTheMonthBefore() {
        val expenses = listOf(
            spent(LocalDate.of(2026, 9, 1), 2_000_00, rent),
            spent(LocalDate.of(2026, 9, 12), 500_00, groceries),
            spent(LocalDate.of(2026, 9, 13), 50_00, null),
            spent(LocalDate.of(2026, 8, 12), 800_00, groceries),
        )
        val top = review(emptyList(), expenses).topCategories
        assertEquals(listOf(rent.id, groceries.id, null), top.map { it.categoryId })
        assertAmount("500", top[1].amount)
        assertAmount("800", top[1].previous)
        assertAmount("0", top[0].previous)
    }

    @Test
    fun noIncomeMeansNoSavingsRate() {
        assertNull(review(emptyList(), listOf(spent(LocalDate.of(2026, 9, 1), 100_00, rent))).savingsRate)
    }

    @Test
    fun accountsWithoutABalanceInTheMonthAreListedUnlessCalculated() {
        val updated = valued(1, AssetKind.ASSET, LocalDate.of(2026, 9, 30) to 1_00)
        val stale = valued(2, AssetKind.ASSET, LocalDate.of(2026, 7, 31) to 1_00)
        val pension = valued(3, AssetKind.ASSET, LocalDate.of(2025, 12, 31) to 1_00)
        val later = valued(4, AssetKind.ASSET, LocalDate.of(2026, 10, 5) to 1_00) // only after the month: not yet then
        val review = review(listOf(updated, stale, pension, later), emptyList(), calculated = setOf(3))
        assertEquals(listOf("Account 2"), review.notUpdated.map { it.name })
    }
}
