package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class IsoCurrenciesTest {

    @Test
    fun findsKnownCurrenciesCaseInsensitively() {
        val inr = IsoCurrencies.lookup(" inr ", Locale.ENGLISH)!!
        assertEquals("INR", inr.code)
        assertEquals("Indian Rupee", inr.name)
        assertEquals(2, inr.decimals)
    }

    @Test
    fun usesCurrencySpecificDecimals() {
        assertEquals(0, IsoCurrencies.lookup("JPY", Locale.ENGLISH)!!.decimals)
    }

    @Test
    fun rejectsUnknownCodes() {
        assertNull(IsoCurrencies.lookup("XYZ1"))
        assertNull(IsoCurrencies.lookup("ABC"))
        assertNull(IsoCurrencies.lookup(""))
    }
}
