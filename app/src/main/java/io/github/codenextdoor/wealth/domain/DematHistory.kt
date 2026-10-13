package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * A demat statement's shares: each ISIN's quantity after every change from [from]
 * (the opening first), and its price on [asOn] from the statement's own holdings.
 */
data class DematHoldings(
    val from: LocalDate,
    val asOn: LocalDate,
    val quantities: Map<String, List<Pair<LocalDate, BigDecimal>>>,
    val prices: Map<String, BigDecimal>,
)

/** Month-end values of demat holdings: exact quantities × each share's close that month. */
object DematHistory {

    /**
     * [values] by month-end; [atStatementPrice]: ISINs valued at the statement's price (no close found);
     * [leftOut]: month-ends with a share that has no price at all (not guessed).
     */
    data class Result(val values: List<Pair<LocalDate, BigDecimal>>, val atStatementPrice: Set<String>, val leftOut: Int = 0)

    /** A close older than this isn't that month's (e.g. a share no longer traded). */
    private const val STALE_DAYS = 10L

    /**
     * Each month-end from [DematHoldings.from] to before [DematHoldings.asOn] (that day has the
     * statement's own total). [closes]: per ISIN, daily closes as they were that day (not split-adjusted).
     * A share without one uses its statement price; a month with a share that has neither is left out.
     */
    fun monthEnds(holdings: DematHoldings, closes: Map<String, Map<LocalDate, BigDecimal>>): Result {
        val atStatementPrice = mutableSetOf<String>()
        var leftOut = 0
        val values = generateSequence(YearMonth.from(holdings.from)) { it.plusMonths(1) }
            .map { it.atEndOfMonth() }
            .takeWhile { it.isBefore(holdings.asOn) }
            .mapNotNull { day ->
                var sum = BigDecimal.ZERO.setScale(2)
                for ((isin, changes) in holdings.quantities) {
                    val units = changes.lastOrNull { !it.first.isAfter(day) }?.second ?: continue
                    if (units.signum() == 0) continue
                    val close = closes[isin]?.filterKeys { !it.isAfter(day) && it.isAfter(day.minusDays(STALE_DAYS)) }?.maxByOrNull { it.key }?.value
                    val price = close ?: holdings.prices[isin]?.also { atStatementPrice += isin } ?: run { leftOut++; return@mapNotNull null }
                    sum += (units * price).setScale(2, RoundingMode.HALF_EVEN)
                }
                day to sum
            }
            .toList()
        return Result(values, atStatementPrice, leftOut)
    }
}
