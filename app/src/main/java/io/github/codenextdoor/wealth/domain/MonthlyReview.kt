package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.YearMonth

/**
 * A finished month at a glance: how net worth moved (end of the month before → end of
 * this month) and which accounts moved it, the month's income, spending and savings
 * against the month before, the biggest spending categories, and accounts without a
 * balance in the month (so their part may be out of date). Amounts in the base currency.
 */
data class MonthlyReview(
    val month: YearMonth,
    val startNetWorth: BigDecimal,
    val endNetWorth: BigDecimal,
    /** Null when the month started at zero. */
    val changePercent: BigDecimal?,
    /** Largest change first (debts growing count as a fall). */
    val movers: List<Pair<ValuedAccount, BigDecimal>>,
    val income: BigDecimal,
    val spending: BigDecimal,
    /** Saved / income × 100, as the Spending tab shows it; null without income. */
    val savingsRate: BigDecimal?,
    val previousIncome: BigDecimal,
    val previousSpending: BigDecimal,
    val previousSavingsRate: BigDecimal?,
    val topCategories: List<CategoryChange>,
    val notUpdated: List<Account>,
) {
    val change: BigDecimal get() = endNetWorth - startNetWorth
    val saved: BigDecimal get() = income - spending

    /** [categoryId] null = uncategorized. */
    data class CategoryChange(val categoryId: Long?, val amount: BigDecimal, val previous: BigDecimal)

    companion object {
        const val MOVERS = 3
        const val TOP_CATEGORIES = 5
        private val HUNDRED = BigDecimal(100)
        private val MATH = MathContext.DECIMAL64

        fun of(
            month: YearMonth,
            calculator: NetWorthCalculator,
            /** At least this month's and the month before's; others are ignored. */
            expenses: List<Expense>,
            categories: Map<Long, ExpenseCategory>,
            rates: RateBook,
            baseCurrency: String,
            decimalsOf: (String) -> Int,
            /** Accounts whose value is worked out (calculated loans, growing pensions, houses). */
            calculated: Set<Long> = emptySet(),
            loanInterest: ((categoryId: Long, month: YearMonth) -> Pair<Long, String>?)? = null,
        ): MonthlyReview {
            val start = month.minusMonths(1).atEndOfMonth()
            val end = month.atEndOfMonth()
            fun flowOf(m: YearMonth) =
                CashFlow.of(expenses.filter { YearMonth.from(it.date) == m }, categories, rates, baseCurrency, decimalsOf, loanInterest)
            val now = flowOf(month)
            val before = flowOf(month.minusMonths(1))
            val startNetWorth = calculator.netWorthAt(start)
            val previousByCategory = before.spendingSummary.byCategory
            return MonthlyReview(
                month = month,
                startNetWorth = startNetWorth,
                endNetWorth = calculator.netWorthAt(end),
                changePercent = startNetWorth.takeIf { it.signum() != 0 }?.let { (calculator.netWorthAt(end) - it).multiply(HUNDRED).divide(it.abs(), MATH) },
                movers = calculator.changesBetween(start, end).take(MOVERS),
                income = now.incomeTotal,
                spending = now.spendingSummary.total,
                savingsRate = now.savedPercent,
                previousIncome = before.incomeTotal,
                previousSpending = before.spendingSummary.total,
                previousSavingsRate = before.savedPercent,
                topCategories = now.spendingSummary.byCategory.entries
                    .sortedByDescending { it.value }
                    .take(TOP_CATEGORIES)
                    .map { (id, amount) -> CategoryChange(id, amount, previousByCategory[id] ?: BigDecimal.ZERO) },
                notUpdated = calculator.included
                    .filter { it.account.id !in calculated }
                    .filter { valued ->
                        val upToEnd = valued.history.filter { it.date <= end }
                        upToEnd.isNotEmpty() && upToEnd.maxOf { it.date } < month.atDay(1)
                    }
                    .map { it.account },
            )
        }
    }
}
