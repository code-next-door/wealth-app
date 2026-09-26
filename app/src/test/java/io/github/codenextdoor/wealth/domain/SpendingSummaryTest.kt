package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SpendingSummaryTest {

    private val day = LocalDate.of(2026, 9, 1)

    private fun expense(amountMinor: Long, currency: String, categoryId: Long?) =
        Expense(0, day, amountMinor, currency, "x", categoryId, false, null, null)

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun totalsByCategoryInBaseCurrency() {
        val rates = RateBook(listOf(RatePoint("CHF", "INR", BigDecimal("100"), day)))
        val summary = SpendingSummary.of(
            listOf(
                expense(50_00, "CHF", 1),
                expense(10_000_00, "INR", 1), // CHF 100
                expense(20_00, "CHF", null),
                expense(-5_00, "CHF", null), // refund
                expense(99_00, "USD", 2), // no rate
            ),
            rates,
            "CHF",
        ) { 2 }
        assertAmount("165", summary.total)
        assertAmount("150", summary.byCategory[1L])
        assertAmount("15", summary.byCategory[null])
        assertEquals(1, summary.excludedCount)
    }
}
