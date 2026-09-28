package io.github.codenextdoor.wealth.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import java.math.BigDecimal
import java.util.Locale

/** Robolectric: formatting uses Android's ICU (the same data as on the phone, e.g. lakh grouping). */
@RunWith(AndroidJUnit4::class)
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

    private val english = Locale.US
    private val german = Locale.GERMANY

    @Test
    fun eachCurrencyInItsHomeStyle() {
        assertEquals("CHF 1’234.50", formatMoney(BigDecimal("1234.505"), "CHF", 2, english).withPlainSpaces()) // rounds half-even
        assertEquals("₹12,34,567.00", formatMoney(BigDecimal("1234567"), "INR", 2, english).withPlainSpaces()) // lakh grouping
        assertEquals("$1,234.50", formatMoney(BigDecimal("1234.5"), "USD", 2, english).withPlainSpaces())
        assertEquals("¥1,234", formatMoney(BigDecimal("1234.5"), "JPY", 0, english).withPlainSpaces()) // half-even: .5 goes to the even yen
    }

    @Test
    fun currenciesOfNoSingleCountryFollowThePhone() {
        assertEquals("€1,234.50", formatMoney(BigDecimal("1234.5"), "EUR", 2, english).withPlainSpaces())
        assertEquals("1.234,50 €", formatMoney(BigDecimal("1234.5"), "EUR", 2, german).withPlainSpaces())
    }

    @Test
    fun phoneLanguageWithTheCurrencysCountryOrEnglishWhenThereIsNoSuchStyle() {
        assertEquals(Locale.forLanguageTag("de-CH"), moneyLocale("CHF", german))
        assertEquals(Locale.forLanguageTag("en-IN"), moneyLocale("INR", german)) // no German-for-India style
        assertEquals("₹12,34,567.00", formatMoney(BigDecimal("1234567"), "INR", 2, german).withPlainSpaces())
        assertEquals(german, moneyLocale("EUR", german))
    }

    @Test
    fun shortFormsForChartAxes() {
        assertEquals("CHF 1.25M", formatMoneyShort(BigDecimal("1250000"), "CHF", english).withPlainSpaces())
        assertEquals("CHF 45K", formatMoneyShort(BigDecimal("45000"), "CHF", english).withPlainSpaces())
        assertEquals("₹12.5L", formatMoneyShort(BigDecimal("1250000"), "INR", english).withPlainSpaces()) // lakh
        assertEquals("₹1.2Cr", formatMoneyShort(BigDecimal("12000000"), "INR", english).withPlainSpaces()) // crore
        assertEquals("CHF 0", formatMoneyShort(BigDecimal.ZERO, "CHF", english).withPlainSpaces())
    }

    @Test
    fun percentagesFollowThePhone() {
        assertEquals("3.3%", formatPercent(BigDecimal("3.26"), locale = english).withPlainSpaces())
        assertEquals("3,3 %", formatPercent(BigDecimal("3.26"), locale = german).withPlainSpaces())
        assertEquals("12.0%", formatPercent(BigDecimal("12"), locale = english).withPlainSpaces())
    }

    @Test
    fun anUnknownCodeStillShowsTheAmount() {
        assertTrue(formatMoney(BigDecimal("5"), "ABC", 2, english).contains("5.00"))
    }
}
