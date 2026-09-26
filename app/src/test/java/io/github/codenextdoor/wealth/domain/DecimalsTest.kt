package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DecimalsTest {

    @Test
    fun parsesDotAndCommaDecimals() {
        assertEquals(BigDecimal("0.0095"), parsePositiveDecimal("0.0095"))
        assertEquals(BigDecimal("105.26"), parsePositiveDecimal(" 105,26 "))
    }

    @Test
    fun ignoresSwissThousandsSeparator() {
        assertEquals(BigDecimal("1000.50"), parsePositiveDecimal("1'000.50"))
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
