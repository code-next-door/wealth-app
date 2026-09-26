package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyTest {

    @Test
    fun parsesAmountsIntoMinorUnits() {
        assertEquals(123450L, parseAmountToMinor("1,234.50", 2))
        assertEquals(123450L, parseAmountToMinor("1'234.5", 2))
        assertEquals(1234567800L, parseAmountToMinor("1,23,45,678", 2))
        assertEquals(0L, parseAmountToMinor("0", 2))
        assertEquals(-5000L, parseAmountToMinor("-50", 2))
        assertEquals(1500L, parseAmountToMinor("1500", 0))
    }

    @Test
    fun rejectsTooManyDecimalsAndText() {
        assertNull(parseAmountToMinor("1.234", 2))
        assertNull(parseAmountToMinor("10.5", 0))
        assertNull(parseAmountToMinor("ten", 2))
        assertNull(parseAmountToMinor("", 2))
    }

    @Test
    fun trailingZerosBeyondDecimalsAreFine() {
        assertEquals(1050L, parseAmountToMinor("10.500", 2))
    }

    @Test
    fun convertsMinorBackToText() {
        assertEquals("1234.5", minorToInputText(123450, 2))
        assertEquals("0", minorToInputText(0, 2))
        assertEquals(BigDecimal("1234.50"), minorToDecimal(123450, 2))
    }

    @Test
    fun formatsWithCurrencyAndRounding() {
        val text = formatMoney(BigDecimal("1234.505"), "CHF", 2, Locale.US)
        assertTrue(text, text.contains("CHF") && text.contains("1,234.50"))
        // Indian lakh grouping (1,23,456) comes from Android's ICU; the desktop
        // JVM running unit tests groups in thousands, so only check the basics.
        val inr = formatMoney(BigDecimal("123456"), "INR", 2, Locale.forLanguageTag("en-IN"))
        assertTrue(inr, inr.contains("₹") && inr.endsWith("456.00"))
    }
}
