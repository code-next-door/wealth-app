package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DecimalsTest {

    @Test
    fun normalizesCommonFormats() {
        assertEquals("105.26", normalizeNumberInput("105.26"))
        assertEquals("105.26", normalizeNumberInput(" 105,26 "))
        assertEquals("1234.50", normalizeNumberInput("1,234.50"))
        assertEquals("1234.50", normalizeNumberInput("1.234,50"))
        assertEquals("1000.50", normalizeNumberInput("1'000.50"))
        assertEquals("1234.5", normalizeNumberInput("1 234,5"))
        assertEquals("123456.78", normalizeNumberInput("1,23,456.78"))
        assertEquals("1234", normalizeNumberInput("1,234"))
        assertEquals("-50", normalizeNumberInput("-50"))
    }

    @Test
    fun parsesPositiveRates() {
        assertEquals(BigDecimal("0.0095"), parsePositiveDecimal("0.0095"))
        assertEquals(BigDecimal("105.26"), parsePositiveDecimal("105,26"))
    }

    @Test
    fun rejectsZeroNegativeAndText() {
        assertNull(parsePositiveDecimal("0"))
        assertNull(parsePositiveDecimal("-1"))
        assertNull(parsePositiveDecimal("abc"))
        assertNull(parsePositiveDecimal(""))
    }

    @Test
    fun formatsRatesReadably() {
        assertEquals("105.2631579", formatRate(BigDecimal.ONE.divide(BigDecimal("0.0095"), CurrencyConverter.MATH)))
        assertEquals("100", formatRate(BigDecimal("100.000")))
        assertEquals("0.8", formatRate(BigDecimal("0.80")))
    }
}
