package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CurrencyConverterTest {

    private fun rate(from: String, to: String, value: String) =
        ExchangeRate(from, to, BigDecimal(value), Instant.EPOCH)

    private fun assertSameValue(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun sameCurrencyIsOne() {
        assertSameValue("1", CurrencyConverter(emptyList()).rate("CHF", "CHF"))
    }

    @Test
    fun directRate() {
        val converter = CurrencyConverter(listOf(rate("USD", "CHF", "0.8")))
        assertSameValue("80", converter.convert(BigDecimal("100"), "USD", "CHF"))
    }

    @Test
    fun inverseRate() {
        val converter = CurrencyConverter(listOf(rate("CHF", "INR", "100")))
        assertSameValue("0.01", converter.rate("INR", "CHF"))
    }

    @Test
    fun enteredRateWinsOverInverse() {
        // Both directions entered and slightly inconsistent: use what was entered.
        val converter = CurrencyConverter(
            listOf(rate("CHF", "INR", "100"), rate("INR", "CHF", "0.0102")),
        )
        assertSameValue("100", converter.rate("CHF", "INR"))
        assertSameValue("0.0102", converter.rate("INR", "CHF"))
    }

    @Test
    fun convertsThroughIntermediateCurrency() {
        // Rates entered against CHF; base changed to INR.
        val converter = CurrencyConverter(
            listOf(rate("USD", "CHF", "0.8"), rate("INR", "CHF", "0.01")),
        )
        assertSameValue("80", converter.rate("USD", "INR"))
    }

    @Test
    fun unlinkedCurrenciesReturnNull() {
        val converter = CurrencyConverter(listOf(rate("USD", "CHF", "0.8")))
        assertNull(converter.rate("INR", "CHF"))
    }
}
