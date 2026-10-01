package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A pension (Swiss pillar 2, EPF, PPF…) that grows with regular contributions and
 * credited interest: an account of a type that grows with contributions, with these
 * terms. Its balance entries are the known values (e.g. each yearly certificate's
 * vested benefit), which always win (see [PensionValue]).
 */
data class Pension(
    val accountId: Long,
    /** Paid in a year, yours and your employer's together; minor units, in the account's currency. */
    val yearlyContributionMinor: Long,
    /** Interest credited, in percent a year, e.g. 1.25. */
    val yearlyRate: BigDecimal,
)

/**
 * A pension's value on any day, from its known values (certificates):
 * - on a known day: that value;
 * - between two known values: a straight line from one to the next (contributions
 *   arrive every month, so it's close, and exact at both ends);
 * - after the latest: at each month-end, a month's interest on the balance
 *   (yearly rate ÷ 12, rounded to the minor unit, HALF_EVEN) plus a twelfth of the
 *   yearly contributions, until the next known value replaces it;
 * - before the first: 0.
 */
object PensionValue {

    private val TWELVE_HUNDRED = BigDecimal(1200)
    private val TWELVE = BigDecimal(12)

    /** Value at the end of [date], in minor units; [known] are (day, value) pairs in any order. */
    fun at(pension: Pension, known: List<Pair<LocalDate, Long>>, date: LocalDate): Long {
        val sorted = known.sortedBy { it.first }
        val (fromDay, fromValue) = sorted.lastOrNull { !it.first.isAfter(date) } ?: return 0
        if (fromDay == date) return fromValue
        val next = sorted.firstOrNull { it.first.isAfter(date) }
        if (next != null) {
            val total = ChronoUnit.DAYS.between(fromDay, next.first)
            val elapsed = ChronoUnit.DAYS.between(fromDay, date)
            val step = BigDecimal(next.second - fromValue).multiply(BigDecimal(elapsed)).divide(BigDecimal(total), 0, RoundingMode.HALF_EVEN)
            return fromValue + step.toLong()
        }
        val contribution = BigDecimal(pension.yearlyContributionMinor).divide(TWELVE, 0, RoundingMode.HALF_EVEN).toLong()
        var balance = fromValue
        // Every month-end after the known day, up to the date.
        var monthEnd = YearMonth.from(fromDay).atEndOfMonth().let { if (it == fromDay) YearMonth.from(fromDay).plusMonths(1).atEndOfMonth() else it }
        while (!monthEnd.isAfter(date)) {
            val interest = BigDecimal(balance).multiply(pension.yearlyRate).divide(TWELVE_HUNDRED, 0, RoundingMode.HALF_EVEN).toLong()
            balance += interest + contribution
            monthEnd = YearMonth.from(monthEnd).plusMonths(1).atEndOfMonth()
        }
        return balance
    }

    /** Each pension's value on [date], by account id, from all balance entries. */
    fun today(pensions: List<Pension>, entries: List<BalanceEntry>, date: LocalDate): Map<Long, Long> {
        val byAccount = entries.groupBy { it.accountId }
        return pensions.associate { pension ->
            pension.accountId to at(pension, byAccount[pension.accountId].orEmpty().map { it.date to it.balanceMinor }, date)
        }
    }
}
