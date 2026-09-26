package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal

/** Spending totals in the base currency, converted at each expense's date. */
data class SpendingSummary(
    val total: BigDecimal,
    /** Key null = uncategorized. */
    val byCategory: Map<Long?, BigDecimal>,
    /** Expenses left out because their currency has no rate to the base currency. */
    val excludedCount: Int,
) {
    companion object {
        fun of(
            expenses: List<Expense>,
            rates: RateBook,
            baseCurrency: String,
            decimalsOf: (String) -> Int,
        ): SpendingSummary {
            var total = BigDecimal.ZERO
            val byCategory = LinkedHashMap<Long?, BigDecimal>()
            var excluded = 0
            expenses.forEach { expense ->
                val amount = minorToDecimal(expense.amountMinor, decimalsOf(expense.currencyCode))
                val inBase = rates.converterAt(expense.date).convert(amount, expense.currencyCode, baseCurrency)
                if (inBase == null) {
                    excluded++
                } else {
                    total += inBase
                    byCategory[expense.categoryId] = (byCategory[expense.categoryId] ?: BigDecimal.ZERO) + inBase
                }
            }
            return SpendingSummary(total, byCategory, excluded)
        }
    }
}
