package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * A period's money split three ways, by each row's category (amounts: positive
 * is money out, negative money in):
 * - income: rows in an income category, and money in without a category;
 * - not counted: rows in a category switched off (e.g. transfers to a broker);
 * - spending: the rest. Money in there is a refund and lowers its category.
 * A split expense counts as its parts (see [Expense.countedParts]), each by its own
 * category. All in the base currency at each row's date; rows without a rate are left out.
 */
class CashFlow private constructor(
    /** As saved (an EMI stays one row; [splits] says how it's counted). */
    val spending: List<Expense>,
    val income: List<Expense>,
    /** Includes the principal part of split EMIs (copies with that amount). */
    val notCounted: List<Expense>,
    val spendingSummary: SpendingSummary,
    /** Money in minus money out in income categories. */
    val incomeTotal: BigDecimal,
    /** EMIs in a category linked to a calculated loan, by expense id: interest counted, principal not. */
    val splits: Map<Long, LoanSplit> = emptyMap(),
) {
    val saved: BigDecimal get() = incomeTotal - spendingSummary.total

    /** Saved as a percentage of income (two decimals); null without income. */
    val savedPercent: BigDecimal?
        get() = if (incomeTotal.signum() <= 0) null else saved.multiply(HUNDRED).divide(incomeTotal, 2, RoundingMode.HALF_EVEN)

    enum class Kind { SPENDING, INCOME, NOT_COUNTED }

    /** How an EMI is counted: [interestMinor] is spending, [principalMinor] pays down the loan (in the EMI's currency). */
    data class LoanSplit(val interestMinor: Long, val principalMinor: Long)

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
            /**
             * For categories linked to a calculated loan: the interest of its EMIs in a month
             * (minor units and currency). That much of the month's payments in the category
             * is spending, the first payments first; the rest is principal, not counted.
             */
            loanInterest: ((categoryId: Long, month: YearMonth) -> Pair<Long, String>?)? = null,
        ): CashFlow {
            val byKind = expenses.flatMap { it.countedParts() }.groupBy { kindOf(it, it.categoryId?.let(categories::get)) }
            val spending = byKind[Kind.SPENDING].orEmpty()
            val income = byKind[Kind.INCOME].orEmpty()
            val splits = LinkedHashMap<Long, LoanSplit>()
            val counted = HashMap<Expense, Expense>() // a split EMI -> the part that counts
            val principal = mutableListOf<Expense>()
            if (loanInterest != null) {
                val left = HashMap<Pair<Long, YearMonth>, Long>() // interest still to count, in the payments' currency
                spending.filter { it.amountMinor > 0 && it.categoryId != null }.sortedBy { it.date }.forEach { row ->
                    val month = YearMonth.from(row.date)
                    val (interest, currency) = loanInterest(row.categoryId!!, month) ?: return@forEach
                    val key = row.categoryId to month
                    val remaining = left[key] ?: run {
                        val decimals = decimalsOf(row.currencyCode)
                        rates.converterAt(row.date).convert(minorToDecimal(interest, decimalsOf(currency)), currency, row.currencyCode)
                            ?.movePointRight(decimals)?.setScale(0, RoundingMode.HALF_EVEN)?.toLong()
                    } ?: return@forEach
                    val interestPart = minOf(row.amountMinor, remaining)
                    left[key] = remaining - interestPart
                    splits[row.id] = LoanSplit(interestPart, row.amountMinor - interestPart)
                    counted[row] = row.copy(amountMinor = interestPart)
                    if (row.amountMinor > interestPart) principal += row.copy(amountMinor = row.amountMinor - interestPart)
                }
            }
            return CashFlow(
                spending = spending,
                income = income,
                notCounted = byKind[Kind.NOT_COUNTED].orEmpty() + principal,
                splits = splits,
                spendingSummary = SpendingSummary.of(spending.map { counted[it] ?: it }, rates, baseCurrency, decimalsOf),
                // Stored like spending (money in negative), so income is the negated sum.
                incomeTotal = SpendingSummary.of(income, rates, baseCurrency, decimalsOf).total.negate(),
            )
        }
    }
}
