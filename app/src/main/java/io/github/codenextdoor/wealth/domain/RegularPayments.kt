package io.github.codenextdoor.wealth.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Regular payments found in the transactions: the same shop (as Search's "More from"
 * names it), the same currency and exactly the same amount, charged about monthly
 * (3+ times, 25–35 days apart) or yearly (2+ times, 350–380 days apart). Exact amounts
 * keep groceries and the like out; a price change starts a new one. Only money out in
 * counted spending (not income, not "not counted" categories such as card bills).
 */
object RegularPayments {

    enum class Cadence(val months: Long, val gapDays: IntRange, val minCharges: Int, val perYear: Int) {
        MONTHLY(1, 25..35, 3, 12),
        YEARLY(12, 350..380, 2, 1),
    }

    data class Payment(
        val shop: String,
        val currencyCode: String,
        val amountMinor: Long,
        val cadence: Cadence,
        /** Charges in the regular run (the latest ones). */
        val count: Int,
        val lastDate: LocalDate,
        val nextDate: LocalDate,
        val yearlyMinor: Long,
    )

    data class Result(val active: List<Payment>, val stopped: List<Payment>, val hidden: List<Payment>)

    /** A payment is stopped once two of its cycles passed without a charge. */
    fun find(
        expenses: List<Expense>,
        categories: Map<Long, ExpenseCategory>,
        categorizer: Categorizer,
        today: LocalDate,
        hiddenShops: Set<String>,
    ): Result {
        val payments = expenses
            .filter { it.amountMinor > 0 && it.parts.isEmpty() }
            .filter { CashFlow.kindOf(it, it.categoryId?.let(categories::get)) == CashFlow.Kind.SPENDING }
            .mapNotNull { expense -> TransactionSearch.shopOf(expense.description, categorizer, moneyOut = true)?.let { it to expense } }
            .groupBy({ (shop, e) -> Triple(shop, e.currencyCode, e.amountMinor) }, { it.second.date })
            .mapNotNull { (key, dates) -> regular(key.first, key.second, key.third, dates) }

        val (hidden, shown) = payments.partition { it.shop in hiddenShops }
        val (stopped, active) = shown.partition { today.isAfter(it.lastDate.plusMonths(2 * it.cadence.months)) }
        val bySize = compareByDescending<Payment> { it.yearlyMinor }.thenBy { it.shop }
        return Result(active.sortedWith(bySize), stopped.sortedByDescending { it.lastDate }, hidden.sortedBy { it.shop })
    }

    /** The latest run of charges in a rhythm; an older irregular one doesn't break it. */
    private fun regular(shop: String, currency: String, amountMinor: Long, dates: List<LocalDate>): Payment? {
        val days = dates.distinct().sortedDescending()
        return Cadence.entries.firstNotNullOfOrNull { cadence ->
            var run = 1
            while (run < days.size && ChronoUnit.DAYS.between(days[run], days[run - 1]).toInt() in cadence.gapDays) run++
            if (run < cadence.minCharges) return@firstNotNullOfOrNull null
            Payment(
                shop = shop,
                currencyCode = currency,
                amountMinor = amountMinor,
                cadence = cadence,
                count = run,
                lastDate = days.first(),
                nextDate = days.first().plusMonths(cadence.months),
                yearlyMinor = amountMinor * cadence.perYear,
            )
        }
    }
}
