package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A straight-line trend through net worth history, fitted with least squares.
 * Deliberately simple: it extrapolates the recent slope, nothing more.
 */
data class Trend(
    /** Average change in base currency per day. */
    val slopePerDay: BigDecimal,
) {
    /** Average change per month (365.25 / 12 days). */
    val perMonth: BigDecimal get() = slopePerDay.multiply(DAYS_PER_MONTH, CurrencyConverter.MATH)

    /** Projects [current] forward from [from] to [date] along the trend. */
    fun project(current: BigDecimal, from: LocalDate, date: LocalDate): BigDecimal =
        current + slopePerDay.multiply(BigDecimal(ChronoUnit.DAYS.between(from, date)), CurrencyConverter.MATH)

    companion object {
        private val DAYS_PER_MONTH = BigDecimal("30.4375")

        /** Minimum history needed before a trend means anything. */
        const val MIN_SPAN_DAYS = 28L

        /**
         * Fits a line through [points]. Returns null with fewer than two points
         * or when they span less than [MIN_SPAN_DAYS].
         */
        fun fit(points: List<Pair<LocalDate, BigDecimal>>): Trend? {
            if (points.size < 2) return null
            val origin = points.first().first
            if (ChronoUnit.DAYS.between(origin, points.last().first) < MIN_SPAN_DAYS) return null

            val mc = CurrencyConverter.MATH
            val n = BigDecimal(points.size)
            var sumX = BigDecimal.ZERO
            var sumY = BigDecimal.ZERO
            var sumXY = BigDecimal.ZERO
            var sumXX = BigDecimal.ZERO
            points.forEach { (date, y) ->
                val x = BigDecimal(ChronoUnit.DAYS.between(origin, date))
                sumX += x
                sumY += y
                sumXY += x.multiply(y, mc)
                sumXX += x.multiply(x, mc)
            }
            val denominator = n.multiply(sumXX, mc) - sumX.multiply(sumX, mc)
            if (denominator.signum() == 0) return null
            val slope = (n.multiply(sumXY, mc) - sumX.multiply(sumY, mc)).divide(denominator, mc)
            return Trend(slope)
        }
    }
}
