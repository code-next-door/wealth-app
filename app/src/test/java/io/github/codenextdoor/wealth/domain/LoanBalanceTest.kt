package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

class LoanBalanceTest {

    private val first = LocalDate.of(2024, 1, 5)

    // ₹50,00,000 at 8.5% over 240 months: the EMI by the standard formula.
    private val principal = 50_00_000_00L
    private val rate = BigDecimal("8.5")
    private val emi = emiFor(principal, rate, 240)

    private fun loan(changes: List<LoanRateChange> = emptyList(), emiMinor: Long = emi) =
        Loan(1, principal, first, emiMinor, rate, rateChanges = changes)

    /** Known balances: the principal on the first EMI day (before that EMI). */
    private val start = listOf(first.minusDays(1) to principal)

    /** Standard EMI: P·r·(1+r)^n / ((1+r)^n − 1), r monthly. */
    private fun emiFor(p: Long, yearly: BigDecimal, n: Int): Long {
        val r = yearly.divide(BigDecimal(1200), MathContext.DECIMAL128)
        val f = (BigDecimal.ONE + r).pow(n, MathContext.DECIMAL128)
        return BigDecimal(p).multiply(r).multiply(f).divide(f - BigDecimal.ONE, 0, RoundingMode.HALF_EVEN).toLong()
    }

    /** Outstanding after k EMIs by the closed formula (no rounding per step): B·(1+r)^k − EMI·((1+r)^k − 1)/r. */
    private fun formulaAfter(k: Int): BigDecimal {
        val r = rate.divide(BigDecimal(1200), MathContext.DECIMAL128)
        val f = (BigDecimal.ONE + r).pow(k, MathContext.DECIMAL128)
        return BigDecimal(principal).multiply(f) - BigDecimal(emi).multiply(f - BigDecimal.ONE).divide(r, MathContext.DECIMAL128)
    }

    @Test
    fun theOutstandingFollowsTheAmortizationFormula() {
        for (k in listOf(1, 12, 60)) {
            val day = first.plusMonths((k - 1).toLong())
            val calculated = LoanBalance.at(loan(), start, day)
            // Rounding each month to the paisa drifts at most a few paise from the exact formula.
            val drift = (BigDecimal(calculated) - formulaAfter(k)).abs()
            check(drift <= BigDecimal(k)) { "after $k EMIs: $calculated vs ${formulaAfter(k)}" }
        }
        assertEquals(principal, LoanBalance.at(loan(), start, first.minusDays(1))) // before the first EMI
        assertEquals(0, LoanBalance.at(loan(), start, first.minusDays(2))) // before the loan
    }

    @Test
    fun aRateChangeKeepsTheEmiSoTheLoanRunsLonger() {
        val higher = loan(listOf(LoanRateChange(1, LocalDate.of(2025, 1, 1), BigDecimal("9.5"), null)))
        val day = LocalDate.of(2026, 1, 5)
        val withChange = LoanBalance.at(higher, start, day)
        val without = LoanBalance.at(loan(), start, day)
        check(withChange > without) { "a higher rate with the same EMI pays off less" }
        // Same EMI before and after the change.
        val payments = LoanBalance.payments(higher, start.single().first, principal, day)
        assertEquals(setOf(emi), payments.map { it.interestMinor + it.principalMinor }.toSet())
    }

    @Test
    fun aRateChangeCanSetANewEmi() {
        val change = LoanRateChange(1, LocalDate.of(2025, 1, 1), BigDecimal("8.0"), 40_000_00L)
        val payments = LoanBalance.payments(loan(listOf(change)), start.single().first, principal, LocalDate.of(2025, 3, 5))
        assertEquals(emi, payments.first { it.date == LocalDate.of(2024, 12, 5) }.let { it.interestMinor + it.principalMinor })
        assertEquals(40_000_00L, payments.first { it.date == LocalDate.of(2025, 1, 5) }.let { it.interestMinor + it.principalMinor })
    }

    @Test
    fun withoutAChangeThePreviousRateContinues() {
        val change = LoanRateChange(1, LocalDate.of(2024, 6, 1), BigDecimal("9.0"), null)
        val payments = LoanBalance.payments(loan(listOf(change)), start.single().first, principal, LocalDate.of(2024, 9, 5))
        // July–September: still 9% (the balance before × 9% ÷ 12).
        val july = payments.first { it.date == LocalDate.of(2024, 7, 5) }
        val june = payments.first { it.date == LocalDate.of(2024, 6, 5) }
        assertEquals(BigDecimal(june.balanceAfterMinor).multiply(BigDecimal("9.0")).divide(BigDecimal(1200), 0, RoundingMode.HALF_EVEN).toLong(), july.interestMinor)
    }

    @Test
    fun aKnownBalanceWinsAndTheCalculationContinuesFromIt() {
        // A prepayment on 15 Mar 2026: the bank's new outstanding is known.
        val known = start + (LocalDate.of(2026, 3, 15) to 40_00_000_00L)
        assertEquals(40_00_000_00L, LoanBalance.at(loan(), known, LocalDate.of(2026, 3, 20)))
        val april = LoanBalance.at(loan(), known, LocalDate.of(2026, 4, 5))
        val interest = BigDecimal(40_00_000_00L).multiply(rate).divide(BigDecimal(1200), 0, RoundingMode.HALF_EVEN).toLong()
        assertEquals(40_00_000_00L + interest - emi, april)
    }

    @Test
    fun itNeverGoesBelowZeroAndStops() {
        val small = Loan(1, 1_000_00L, first, 600_00L, BigDecimal("12"))
        val known = listOf(first.minusDays(1) to 1_000_00L)
        assertEquals(0, LoanBalance.at(small, known, first.plusMonths(5)))
        val payments = LoanBalance.payments(small, first.minusDays(1), 1_000_00L, first.plusMonths(5))
        assertEquals(2, payments.size) // paid off in the second month, nothing after
    }

    @Test
    fun emisOnTheLastDayOfShorterMonths() {
        val endOfMonth = Loan(1, principal, LocalDate.of(2024, 1, 31), emi, rate)
        val dates = LoanBalance.emiDates(endOfMonth).take(4).toList()
        assertEquals(listOf(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30)), dates)
    }

    @Test
    fun interestInAMonth() {
        val march = LoanBalance.interestIn(loan(), start, YearMonth.of(2024, 3))
        val payments = LoanBalance.payments(loan(), start.single().first, principal, LocalDate.of(2024, 3, 31))
        assertEquals(payments.single { it.date == LocalDate.of(2024, 3, 5) }.interestMinor, march)
        assertEquals(0, LoanBalance.interestIn(loan(), start, YearMonth.of(2023, 12)))
    }

    @Test
    fun aPrepaymentLeavesTheCalculatedOutstandingMinusTheAmount() {
        val day = LocalDate.of(2026, 3, 15)
        val before = LoanBalance.at(loan(), start, day)
        assertEquals(before - 2_00_000_00L, LoanBalance.afterPrepayment(loan(), start, day, 2_00_000_00L))
        assertEquals(0, LoanBalance.afterPrepayment(loan(), start, day, before + 1)) // paid off, not negative
    }
}
