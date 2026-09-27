package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SharesTest {

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    private val start = LocalDate.of(2025, 1, 25)

    private fun grant(total: String, months: Int, every: Int = 1, cliff: Int = 0) =
        Grant(1, "New hire", "GOOG", "USD", start.minusDays(10), BigDecimal(total), start, months, every, cliff, null)

    @Test
    fun monthlyVestsAreEqualAndAddUpExactly() {
        val vests = Vesting.schedule(grant("100", 48))
        assertEquals(48, vests.size)
        assertEquals(start.plusMonths(1), vests.first().date)
        assertEquals(start.plusMonths(48), vests.last().date)
        assertAmount("2.083", vests.first().units)
        assertAmount("100", vests.fold(BigDecimal.ZERO) { sum, v -> sum + v.units }) // the last vest takes the remainder
    }

    @Test
    fun aCliffVestsTheWaitedMonthsTogether() {
        val vests = Vesting.schedule(grant("48", 48, cliff = 12))
        assertEquals(37, vests.size)
        assertEquals(start.plusMonths(12), vests.first().date)
        assertAmount("12", vests.first().units)
        assertAmount("1", vests[1].units)
    }

    @Test
    fun quarterlyVests() {
        val vests = Vesting.schedule(grant("40", 12, every = 3))
        assertEquals(listOf(3L, 6L, 9L, 12L), vests.map { java.time.temporal.ChronoUnit.MONTHS.between(start, it.date) })
        assertAmount("10", vests.first().units)
    }

    @Test
    fun unvestedAndNextVestOnADay() {
        val g = grant("48", 48)
        val day = start.plusMonths(10).plusDays(3)
        assertAmount("38", Vesting.unvested(g, day))
        assertEquals(start.plusMonths(11), Vesting.next(g, day)!!.date)
        assertAmount("0", Vesting.unvested(g, start.plusMonths(48)))
        assertNull(Vesting.next(g, start.plusMonths(48)))
    }

    @Test
    fun priceBookUsesTheLatestPriceOnOrBeforeADay() {
        val book = PriceBook(
            listOf(
                PricePoint("GOOG", LocalDate.of(2025, 3, 28), BigDecimal("150"), fetched = true),
                PricePoint("GOOG", LocalDate.of(2025, 3, 31), BigDecimal("156"), fetched = false),
                PricePoint("MSFT", LocalDate.of(2025, 3, 31), BigDecimal("400")),
            ),
        )
        assertAmount("150", book.priceAt("GOOG", LocalDate.of(2025, 3, 30)))
        assertAmount("156", book.priceAt("GOOG", LocalDate.of(2025, 4, 2)))
        assertAmount("150", book.priceAt("GOOG", LocalDate.of(2025, 1, 1))) // before the first: the earliest
        assertNull(book.priceAt("AAPL", LocalDate.of(2025, 4, 2)))
        assertEquals(false, book.pointAt("GOOG", LocalDate.of(2025, 4, 2))!!.fetched)
    }

    @Test
    fun sharesAccountIsWorthSharesTimesPricePlusCash() {
        val day = LocalDate.of(2025, 3, 31)
        val account = ValuedAccount(
            account = Account(1, "Stock plan", 1, "USD", null, 50_00, Instant.EPOCH, null, null, shareSymbol = "GOOG", units = BigDecimal("10")),
            kind = AssetKind.ASSET,
            decimals = 2,
            history = listOf(
                BalanceEntry(1, 1, day.minusMonths(3), 20_00, units = BigDecimal("8")),
                BalanceEntry(2, 1, day, 50_00, units = BigDecimal("10")),
            ),
        )
        val prices = PriceBook(listOf(PricePoint("GOOG", day.minusMonths(3), BigDecimal("100")), PricePoint("GOOG", day, BigDecimal("150"))))
        val rates = RateBook(listOf(RatePoint("USD", "CHF", BigDecimal("0.9"), day.minusYears(1))))
        val calc = NetWorthCalculator(listOf(account), rates, "CHF", prices)
        assertAmount("1395", calc.valueAt(account, day)) // (10 × 150 + 50) × 0.9
        assertAmount("738", calc.valueAt(account, day.minusDays(1))) // (8 × 100 + 20) × 0.9

        // Without any price the account can't be valued, like a missing exchange rate.
        val noPrice = NetWorthCalculator(listOf(account), rates, "CHF")
        assertEquals(listOf(account), noPrice.excluded)
    }
}
