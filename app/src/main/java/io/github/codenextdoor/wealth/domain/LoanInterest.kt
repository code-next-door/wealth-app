package io.github.codenextdoor.wealth.domain

import java.time.YearMonth

/**
 * For categories linked to a calculated loan (its EMIs are imported there): the loan's
 * EMI interest in a month, in minor units and the loan's currency; null for other
 * categories. What [CashFlow] needs to count only the interest as spending.
 */
object LoanInterest {
    fun lookup(loans: List<Loan>, entries: List<BalanceEntry>, accounts: List<Account>): (Long, YearMonth) -> Pair<Long, String>? {
        val history = entries.groupBy { it.accountId }
        val currencyOf = accounts.associate { it.id to it.currencyCode }
        val byCategory = loans.filter { it.emiCategoryId != null }.associateBy { it.emiCategoryId!! }
        val cache = HashMap<Pair<Long, YearMonth>, Pair<Long, String>?>()
        return { categoryId, month ->
            cache.getOrPut(categoryId to month) {
                val loan = byCategory[categoryId]
                val currency = loan?.let { currencyOf[it.accountId] }
                if (loan == null || currency == null) {
                    null
                } else {
                    val known = history[loan.accountId].orEmpty().map { it.date to it.balanceMinor }
                    LoanBalance.interestIn(loan, known, month) to currency
                }
            }
        }
    }
}
