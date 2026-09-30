package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A period's money split three ways, by each row's category (amounts: positive
 * is money out, negative money in):
 * - income: rows in an income category, and money in without a category;
 * - not counted: rows in a category switched off (e.g. transfers to a broker);
 * - spending: the rest. Money in there is a refund and lowers its category.
 * All in the base currency at each row's date; rows without a rate are left out.
 */
class CashFlow private constructor(
    val spending: List<Expense>,
    val income: List<Expense>,
    val notCounted: List<Expense>,
    val spendingSummary: SpendingSummary,
    /** Money in minus money out in income categories. */
    val incomeTotal: BigDecimal,
) {
    val saved: BigDecimal get() = incomeTotal - spendingSummary.total

    /** Saved as a percentage of income (two decimals); null without income. */
    val savedPercent: BigDecimal?
        get() = if (incomeTotal.signum() <= 0) null else saved.multiply(HUNDRED).divide(incomeTotal, 2, RoundingMode.HALF_EVEN)

    enum class Kind { SPENDING, INCOME, NOT_COUNTED }

    companion object {
        private val HUNDRED = BigDecimal(100)

        fun kindOf(expense: Expense, category: ExpenseCategory?): Kind = when {
            category?.isIncome == true -> Kind.INCOME
            category?.countsAsSpending == false -> Kind.NOT_COUNTED
            category == null && expense.amountMinor < 0 -> Kind.INCOME
            else -> Kind.SPENDING
        }

        fun of(
            expenses: List<Expense>,
            categories: Map<Long, ExpenseCategory>,
            rates: RateBook,
            baseCurrency: String,
            decimalsOf: (String) -> Int,
        ): CashFlow {
            val byKind = expenses.groupBy { kindOf(it, it.categoryId?.let(categories::get)) }
            val spending = byKind[Kind.SPENDING].orEmpty()
            val income = byKind[Kind.INCOME].orEmpty()
            return CashFlow(
                spending = spending,
                income = income,
                notCounted = byKind[Kind.NOT_COUNTED].orEmpty(),
                spendingSummary = SpendingSummary.of(spending, rates, baseCurrency, decimalsOf),
                // Stored like spending (money in negative), so income is the negated sum.
                incomeTotal = SpendingSummary.of(income, rates, baseCurrency, decimalsOf).total.negate(),
            )
        }
    }
}
