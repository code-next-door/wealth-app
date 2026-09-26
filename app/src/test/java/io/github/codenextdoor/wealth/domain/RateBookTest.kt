package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateBookTest {

    private val jan: LocalDate = LocalDate.of(2026, 1, 1)
    private val jun: LocalDate = LocalDate.of(2026, 6, 1)

    private fun assertRate(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    private val book = RateBook(
        listOf(
            RatePoint("CHF", "INR", BigDecimal("95"), jan),
            // Entered the other way round later; still the same pair.
            RatePoint("INR", "CHF", BigDecimal("0.01"), jun),
            RatePoint("USD", "CHF", BigDecimal("0.9"), jan),
        ),
    )

    @Test
    fun usesLatestRateOnOrBeforeDate() {
        assertRate("95", book.converterAt(jan).rate("CHF", "INR"))
        assertRate("95", book.converterAt(jun.minusDays(1)).rate("CHF", "INR"))
        assertRate("100", book.converterAt(jun).rate("CHF", "INR"))
        assertRate("100", book.current.rate("CHF", "INR"))
    }

    @Test
    fun beforeFirstRateUsesEarliest() {
        assertRate("95", book.converterAt(jan.minusYears(3)).rate("CHF", "INR"))
    }

    @Test
    fun convertsViaOtherCurrencyOnThatDate() {
        // USD -> CHF -> INR in January: 0.9 * 95
        assertRate("85.5", book.converterAt(jan).rate("USD", "INR"))
    }

    @Test
    fun unknownPairIsNull() {
        assertNull(book.current.rate("EUR", "CHF"))
    }
}
