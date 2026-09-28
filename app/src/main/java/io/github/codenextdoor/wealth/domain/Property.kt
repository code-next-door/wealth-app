package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * A house (or other property): an account of the "Real estate" type with
 * these details. Its value on any day comes from [PropertyValue], using the
 * account's balance entries as anchors (the purchase and any valuations).
 */
data class Property(
    val id: Long,
    val accountId: Long,
    /** Minor units, in the account's currency. */
    val purchasePriceMinor: Long,
    val purchaseDate: LocalDate,
    /** Expected yearly growth after the latest valuation, in percent (e.g. 7). */
    val growthPercent: BigDecimal,
    /** A loan or mortgage account for this house, to show equity (value − loan). */
    val loanAccountId: Long?,
)

/**
 * Estimates a property's value on a day from known values ("anchors": the
 * purchase and any valuations), which always win:
 * - before the first anchor: 0 (not owned yet);
 * - between two anchors: growing at the constant yearly rate that joins them;
 * - after the last: growing at [yearlyGrowthPercent] a year.
 *
 * Growth factors are ratios computed in Double (fine for a factor); the money
 * itself stays BigDecimal.
 */
object PropertyValue {

    /** Calendar years between two days: whole years, plus the part of the next year (so anniversaries are exact). */
    private fun yearsBetween(from: LocalDate, to: LocalDate): Double {
        val whole = ChronoUnit.YEARS.between(from, to)
        val anniversary = from.plusYears(whole)
        val rest = ChronoUnit.DAYS.between(anniversary, to).toDouble() / ChronoUnit.DAYS.between(anniversary, anniversary.plusYears(1))
        return whole + rest
    }

    fun at(anchors: List<Pair<LocalDate, BigDecimal>>, yearlyGrowthPercent: BigDecimal, date: LocalDate): BigDecimal {
        val sorted = anchors.sortedBy { it.first }
        val before = sorted.lastOrNull { !it.first.isAfter(date) } ?: return BigDecimal.ZERO
        val after = sorted.firstOrNull { it.first.isAfter(date) }
        val (fromDate, fromValue) = before
        if (fromDate == date) return fromValue
        val years = yearsBetween(fromDate, date)
        val factor = if (after != null) {
            val (toDate, toValue) = after
            val span = yearsBetween(fromDate, toDate)
            if (fromValue.signum() <= 0 || toValue.signum() <= 0) {
                // No compound rate between zero or negative values: a straight line instead.
                return fromValue + (toValue - fromValue).multiply(BigDecimal(years / span), MathContext.DECIMAL64)
            }
            (toValue.toDouble() / fromValue.toDouble()).pow(years / span)
        } else {
            (1 + yearlyGrowthPercent.toDouble() / 100).pow(years)
        }
        return fromValue.multiply(BigDecimal(factor), MathContext.DECIMAL64)
    }
}
