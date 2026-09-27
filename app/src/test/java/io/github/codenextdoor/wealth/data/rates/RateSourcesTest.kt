package io.github.codenextdoor.wealth.data.rates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Invented responses in each service's format; nothing goes to the internet. */
class RateSourcesTest {

    private val today = LocalDate.of(2026, 3, 16)
    private val saturday = LocalDate.of(2026, 3, 14)

    /** Answers known URLs; records every request. */
    private class FakeHttp(private val responses: Map<String, String>) : HttpGet {
        val requested = mutableListOf<String>()
        override suspend fun get(url: String): String? {
            requested += url
            return responses[url]
        }
    }

    private fun quote(rate: String, date: LocalDate) = Quote(BigDecimal(rate), date)

    @Test
    fun frankfurterGivesTheLastWorkingDaysRates() = runBlocking {
        val http = FakeHttp(
            mapOf(
                "https://api.frankfurter.dev/v1/2026-03-14?base=CHF&symbols=INR,USD" to
                    """{"amount":1.0,"base":"CHF","date":"2026-03-13","rates":{"INR":94.75,"USD":1.0964}}""",
            ),
        )
        val rates = FrankfurterSource(http).ratesOn("CHF", setOf("USD", "INR"), saturday)
        assertEquals(mapOf("INR" to quote("94.75", saturday.minusDays(1)), "USD" to quote("1.0964", saturday.minusDays(1))), rates)
    }

    @Test
    fun currencyApiReadsLowercaseCodesAndUsesLatestForToday() = runBlocking {
        val http = FakeHttp(
            mapOf(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/chf.min.json" to
                    """{"date":"2026-03-16","chf":{"aed":4.0254361,"inr":94.7,"eur":1.07}}""",
            ),
        )
        val rates = CurrencyApiSource(http) { today }.ratesOn("CHF", setOf("AED", "INR"), today)
        assertEquals(mapOf("AED" to quote("4.0254361", today), "INR" to quote("94.7", today)), rates)
    }

    @Test
    fun currencyApiTriesItsMirrorForPastDays() = runBlocking {
        val http = FakeHttp(
            mapOf(
                "https://2026-03-14.currency-api.pages.dev/v1/currencies/chf.min.json" to
                    """{"date":"2026-03-14","chf":{"inr":1.2e2}}""",
            ),
        )
        val rates = CurrencyApiSource(http) { today }.ratesOn("CHF", setOf("INR"), saturday)
        assertEquals(0, BigDecimal("120").compareTo(rates.getValue("INR").rate))
        assertEquals(2, http.requested.size) // the main address failed first
    }

    @Test
    fun fallbackAsksTheSecondSourceOnlyForWhatTheFirstLacks() = runBlocking {
        val first = object : RateSource {
            override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate) = mapOf("INR" to quote("94.75", date))
        }
        val asked = mutableListOf<Set<String>>()
        val second = object : RateSource {
            override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> {
                asked += currencies
                return mapOf("AED" to quote("4.02", date), "INR" to quote("1", date))
            }
        }
        val rates = FallbackRateSource(first, second).ratesOn("CHF", setOf("INR", "AED"), today)
        assertEquals(listOf(setOf("AED")), asked)
        assertEquals(0, BigDecimal("94.75").compareTo(rates.getValue("INR").rate))
        assertEquals(0, BigDecimal("4.02").compareTo(rates.getValue("AED").rate))
    }

    @Test
    fun failuresAndNonsenseGiveNoRates() = runBlocking {
        val broken = FakeHttp(
            mapOf(
                "https://api.frankfurter.dev/v1/2026-03-16?base=CHF&symbols=INR" to "<html>oops</html>",
            ),
        )
        assertTrue(FrankfurterSource(broken).ratesOn("CHF", setOf("INR"), today).isEmpty())
        assertTrue(CurrencyApiSource(FakeHttp(emptyMap())) { today }.ratesOn("CHF", setOf("INR"), today).isEmpty())
        // Zero or negative rates are never accepted.
        val zero = FakeHttp(
            mapOf("https://api.frankfurter.dev/v1/2026-03-16?base=CHF&symbols=INR" to """{"date":"2026-03-16","rates":{"INR":0}}"""),
        )
        assertTrue(FrankfurterSource(zero).ratesOn("CHF", setOf("INR"), today).isEmpty())
        // Nothing to ask for: no request at all.
        val idle = FakeHttp(emptyMap())
        assertTrue(FrankfurterSource(idle).ratesOn("CHF", emptySet(), today).isEmpty())
        assertTrue(idle.requested.isEmpty())
    }
}
