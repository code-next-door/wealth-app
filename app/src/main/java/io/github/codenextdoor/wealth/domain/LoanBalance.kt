package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * A loan whose outstanding the app calculates (home loan, car loan…): an
 * account of a loan type with these terms. Its balance entries are known
 * outstanding amounts, which always win (see [LoanBalance]).
 */
data class Loan(
    val accountId: Long,
    /** Minor units, in the account's currency. */
    val principalMinor: Long,
    /** The first EMI; later EMIs fall on the same day of each month (or the month's last day). */
    val firstEmiDate: LocalDate,
    val emiMinor: Long,
    /** Interest in percent a year, e.g. 8.5. */
    val yearlyRate: BigDecimal,
    /** The category its EMIs are imported into, to split them into interest and principal. */
    val emiCategoryId: Long? = null,
    /** From [LoanRateChange.from]: a new rate, and maybe a new EMI. */
    val rateChanges: List<LoanRateChange> = emptyList(),
)

data class LoanRateChange(val id: Long, val from: LocalDate, val yearlyRate: BigDecimal, val emiMinor: Long?)

/** One EMI as the calculation applies it. */
data class LoanPayment(val date: LocalDate, val interestMinor: Long, val principalMinor: Long, val balanceAfterMinor: Long)

/**
 * Calculates a loan's outstanding on any day, monthly reducing balance:
 * - from the latest known balance on or before the day (known balances
 *   always win; the first is usually the principal on the first EMI day);
 * - each EMI date after it: balance + balance × yearly rate ÷ 12 − EMI,
 *   rounded to minor units, never below zero;
 * - the rate (and EMI) are the latest change on or before that EMI date,
 *   else the loan's own: no change means the previous rate continues, and
 *   the EMI stays (the term moves) unless a change sets a new one.
 * Before the first known balance it's zero.
 */
object LoanBalance {

    private val TWELVE_HUNDRED = BigDecimal(1200)

    /** EMI dates from the first, on its day of the month (the last day in shorter months). */
    fun emiDates(loan: Loan): Sequence<LocalDate> {
        val day = loan.firstEmiDate.dayOfMonth
        val start = YearMonth.from(loan.firstEmiDate)
        return generateSequence(0L) { it + 1 }.map { n ->
            val month = start.plusMonths(n)
            month.atDay(minOf(day, month.lengthOfMonth()))
        }
    }

    private fun termsOn(loan: Loan, date: LocalDate): Pair<BigDecimal, Long> {
        var rate = loan.yearlyRate
        var emi = loan.emiMinor
        loan.rateChanges.sortedBy { it.from }.filter { !it.from.isAfter(date) }.forEach { change ->
            rate = change.yearlyRate
            change.emiMinor?.let { emi = it }
        }
        return rate to emi
    }

    /** The EMIs applied after [from] (exclusive) up to [to] (inclusive), starting at [balanceMinor]. */
    fun payments(loan: Loan, from: LocalDate, balanceMinor: Long, to: LocalDate): List<LoanPayment> {
        var balance = balanceMinor
        val result = mutableListOf<LoanPayment>()
        for (date in emiDates(loan)) {
            if (date.isAfter(to)) break
            if (!date.isAfter(from) || balance <= 0) continue
            val (rate, emi) = termsOn(loan, date)
            val interest = BigDecimal(balance).multiply(rate).divide(TWELVE_HUNDRED, 0, RoundingMode.HALF_EVEN).toLong()
            val paid = minOf(emi, balance + interest)
            val next = balance + interest - paid
            result += LoanPayment(date, interest, paid - interest, next)
            balance = next
        }
        return result
    }

    /** Outstanding at the end of [date], in minor units, from the known balances ([known]: day to minor units). */
    fun at(loan: Loan, known: List<Pair<LocalDate, Long>>, date: LocalDate): Long {
        val (from, balance) = known.filter { !it.first.isAfter(date) }.maxByOrNull { it.first } ?: return 0
        return payments(loan, from, balance, date).lastOrNull()?.balanceAfterMinor ?: balance
    }

    /**
     * The known balance a prepayment of [prepaidMinor] on [date] leaves: the outstanding
     * calculated for that day minus the prepayment (never below zero).
     */
    fun afterPrepayment(loan: Loan, known: List<Pair<LocalDate, Long>>, date: LocalDate, prepaidMinor: Long): Long =
        (at(loan, known, date) - prepaidMinor).coerceAtLeast(0)

    /** Each calculated loan's outstanding at the end of [date], by account (minor units). */
    fun outstanding(loans: List<Loan>, entries: List<BalanceEntry>, date: LocalDate): Map<Long, Long> {
        val byAccount = entries.groupBy { it.accountId }
        return loans.associate { loan ->
            loan.accountId to at(loan, byAccount[loan.accountId].orEmpty().map { it.date to it.balanceMinor }, date)
        }
    }

    /** The interest of the EMIs in [month], in minor units (for splitting EMI payments in spending). */
    fun interestIn(loan: Loan, known: List<Pair<LocalDate, Long>>, month: YearMonth): Long {
        val end = month.atEndOfMonth()
        val sorted = known.sortedBy { it.first }
        // Walk from each known balance to the next, collecting this month's EMIs.
        var total = 0L
        sorted.forEachIndexed { i, (from, balance) ->
            val until = sorted.getOrNull(i + 1)?.first?.minusDays(1)?.let { minOf(it, end) } ?: end
            if (until.isBefore(month.atDay(1))) return@forEachIndexed
            total += payments(loan, from, balance, until).filter { YearMonth.from(it.date) == month }.sumOf { it.interestMinor }
        }
        return total
    }
}
