package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PensionValueTest {

    // CHF 24'000 a year paid in (yours + employer's), 1.25 % interest.
    private val pension = Pension(accountId = 1, yearlyContributionMinor = 24_000_00, yearlyRate = BigDecimal("1.25"))
    private val first = LocalDate.of(2024, 1, 1) to 100_000_00L
    private val second = LocalDate.of(2025, 1, 1) to 136_600_00L

    private fun at(date: LocalDate, vararg known: Pair<LocalDate, Long>) = PensionValue.at(pension, known.toList(), date)

    @Test
    fun nothingBeforeTheFirstCertificate() {
        assertEquals(0L, at(LocalDate.of(2023, 12, 31), first, second))
    }

    @Test
    fun aCertificateIsExactOnItsDay() {
        assertEquals(100_000_00L, at(first.first, first, second))
        assertEquals(136_600_00L, at(second.first, first, second))
    }

    @Test
    fun betweenTwoCertificatesItGrowsInAStraightLine() {
        // 2024 has 366 days; 2 July is day 183: halfway.
        assertEquals(118_300_00L, at(LocalDate.of(2024, 7, 2), first, second))
        // Known values in any order.
        assertEquals(118_300_00L, at(LocalDate.of(2024, 7, 2), second, first))
    }

    @Test
    fun afterTheLatestCertificateEachMonthAddsInterestAndContributions() {
        // Nothing until the first month-end after the certificate.
        assertEquals(136_600_00L, at(LocalDate.of(2025, 1, 30), first, second))
        // 31 Jan: 136'600.00 × 1.25 % / 12 = 142.29 interest, + 2'000.00 contributions.
        assertEquals(138_742_29L, at(LocalDate.of(2025, 1, 31), first, second))
        // 28 Feb: 138'742.29 × 1.25 % / 12 = 144.52, + 2'000.00.
        assertEquals(140_886_81L, at(LocalDate.of(2025, 2, 28), first, second))
        assertEquals(140_886_81L, at(LocalDate.of(2025, 3, 30), first, second))
    }

    @Test
    fun aNewCertificateTakesOver() {
        val third = LocalDate.of(2025, 2, 15) to 150_000_00L // e.g. after a buy-in
        assertEquals(150_000_00L, at(LocalDate.of(2025, 2, 27), first, second, third))
        // From it: 150'000.00 × 1.25 % / 12 = 156.25, + 2'000.00.
        assertEquals(152_156_25L, at(LocalDate.of(2025, 2, 28), first, second, third))
    }

    @Test
    fun withoutContributionsOrInterestItStaysAtTheLatestCertificate() {
        val flat = Pension(1, 0, BigDecimal.ZERO)
        assertEquals(136_600_00L, PensionValue.at(flat, listOf(first, second), LocalDate.of(2027, 6, 30)))
    }

    @Test
    fun todaysValueForEachPension() {
        val entries = listOf(
            BalanceEntry(1, 1, first.first, first.second),
            BalanceEntry(2, 1, second.first, second.second),
            BalanceEntry(3, 2, second.first, 5_00), // another account, not a pension
        )
        assertEquals(mapOf(1L to 138_742_29L), PensionValue.today(listOf(pension), entries, LocalDate.of(2025, 1, 31)))
    }
}
