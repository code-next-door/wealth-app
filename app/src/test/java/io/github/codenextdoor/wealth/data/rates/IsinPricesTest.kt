package io.github.codenextdoor.wealth.data.rates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class IsinPricesTest {

    @Test
    fun anIsinFindsItsNseSymbolFirst() = runBlocking {
        val body = """{"quotes":[{"symbol":"EXAMPLE.BO","exchange":"BSE"},{"symbol":"EXAMPLE.NS","exchange":"NSI"}]}"""
        val asked = mutableListOf<String>()
        assertEquals("EXAMPLE.NS", YahooPriceSource { url -> asked += url; body }.symbolFor("INE000A01011").getOrNull())
        assertTrue(asked.single(), asked.single().contains("search?q=INE000A01011"))
        assertEquals("EXAMPLE.BO", YahooPriceSource { """{"quotes":[{"symbol":"EXAMPLE.BO"}]}""" }.symbolFor("INE000A01011").getOrNull())
        // Not known is an answer; no answer at all is being offline.
        assertTrue(YahooPriceSource { """{"quotes":[]}""" }.symbolFor("INE000A01011").let { it.isSuccess && it.getOrNull() == null })
        assertTrue(YahooPriceSource { null }.symbolFor("INE000A01011").isFailure)
    }

    @Test
    fun closesBeforeASplitAreUndoneToWhatTheyWereThatDay() = runBlocking {
        // 2:1 bonus on 2025-03-31: Yahoo shows 2025-03-28 halved (100); it really closed at 200.
        val body = """
            {"chart":{"result":[{"meta":{"exchangeTimezoneName":"Asia/Kolkata"},
            "timestamp":[1743133500,1743392700],
            "events":{"splits":{"1743392700":{"date":1743392700,"numerator":2,"denominator":1}}},
            "indicators":{"quote":[{"close":[100.0,105.0]}]}}]}}
        """.trimIndent()
        val asked = mutableListOf<String>()
        val closes = YahooPriceSource { url -> asked += url; body }.rawCloses("EXAMPLE.NS", LocalDate.of(2025, 3, 20), LocalDate.of(2025, 3, 31))
        assertEquals(0, BigDecimal("200").compareTo(closes.getValue(LocalDate.of(2025, 3, 28))))
        assertEquals(0, BigDecimal("105").compareTo(closes.getValue(LocalDate.of(2025, 3, 31))))
        assertTrue(asked.single(), asked.single().contains("events=split"))
        // Splits after the statement still adjust its closes, so they're asked for up to today.
        val period2 = Regex("period2=(\\d+)").find(asked.single())!!.groupValues[1].toLong()
        assertTrue(period2 > LocalDate.now().atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC))
    }
}
