package io.github.codenextdoor.wealth.data.rates

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@RunWith(AndroidJUnit4::class)
class RateUpdaterTest : DatabaseTest() {

    /** "1 CHF = 100 INR" and "1 CHF = 1.25 USD" every weekday; nothing on weekends (like the ECB). */
    private class FakeSource(var online: Boolean = true) : RateSource {
        val asked = mutableListOf<Pair<Set<String>, LocalDate>>()
        override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> {
            asked += currencies to date
            if (!online) return emptyMap()
            var day = date
            while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.minusDays(1)
            val all = mapOf("INR" to Quote(BigDecimal("100"), day), "USD" to Quote(BigDecimal("1.25"), day))
            return all.filterKeys { it in currencies }
        }
    }

    private val source = FakeSource()
    private val updater get() = RateUpdater(currencies, accounts, source) { today }

    private fun rateOn(currency: String, date: LocalDate) =
        runBlocking { currencies.rateBook.first().converterAt(date).rate(currency, "CHF") }

    private val saturday = today.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))

    @Test
    fun lookUpGivesAndSavesTheRateForADay() = runBlocking {
        val found = updater.lookUp("INR", saturday)!!
        // Units of the base currency per 1 INR, from the Friday before.
        assertEquals(0, BigDecimal("0.01").compareTo(found.rate))
        assertEquals(saturday.minusDays(1), found.date)
        assertEquals(0, BigDecimal("0.01").compareTo(rateOn("INR", saturday)))
    }

    @Test
    fun lookUpAsksOnlyOncePerDay() = runBlocking {
        val updater = updater
        updater.lookUp("INR", saturday)
        updater.lookUp("INR", saturday)
        assertEquals(1, source.asked.size)
    }

    @Test
    fun aRememberedRateIsSavedAgainIfItWasLostMeanwhile() = runBlocking {
        val updater = updater
        updater.lookUp("INR", today)
        db.backupDao().clearExchangeRates() // e.g. a backup restored since
        updater.lookUp("INR", today)
        assertEquals(1, source.asked.size) // not downloaded again...
        assertEquals(0, BigDecimal("0.01").compareTo(rateOn("INR", today))) // ...but saved again
    }

    @Test
    fun lookUpKeepsATypedRateButStillReportsTheDownloadedOne() = runBlocking {
        setRate("CHF", "INR", "90", saturday.minusDays(1))
        val found = updater.lookUp("INR", saturday)!!
        assertEquals(0, BigDecimal("0.01").compareTo(found.rate))
        assertEquals(0, BigDecimal("90").compareTo(BigDecimal.ONE.divide(rateOn("INR", saturday), java.math.MathContext.DECIMAL64)))
    }

    @Test
    fun nothingToLookUpForTheBaseCurrencyOrWhenOffline() = runBlocking {
        assertNull(updater.lookUp("CHF", today))
        source.online = false
        assertNull(updater.lookUp("INR", today))
        assertTrue(source.asked.single().second == today)
    }

    @Test
    fun refreshGetsTodaysRatesForEveryCurrency() = runBlocking {
        val result = updater.refresh()
        assertTrue(result.reachedSource)
        assertEquals(0, BigDecimal("0.01").compareTo(rateOn("INR", today)))
        assertEquals(0, BigDecimal("0.8").compareTo(rateOn("USD", today)))
        assertEquals(setOf("INR", "USD"), source.asked.first().first)
    }

    @Test
    fun refreshFillsInPastBalanceDatesWithoutARateNearby() = runBlocking {
        val old = today.minusMonths(3)
        addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in", date = old)
        val covered = today.minusMonths(2)
        addAccount("Dollars", currency = "USD", date = covered)
        setRate("CHF", "USD", "1.2", covered.minusDays(2)) // typed, close enough before the balance

        updater.refresh()

        val askedDates = source.asked.map { it.second }
        assertTrue(old in askedDates)
        assertFalse(covered in askedDates)
        assertEquals(0, BigDecimal("0.01").compareTo(rateOn("INR", old)))

        // Second time round there's nothing missing.
        source.asked.clear()
        updater.refresh()
        assertEquals(listOf(today), source.asked.map { it.second })
    }

    @Test
    fun refreshOfflineSaysSo() = runBlocking {
        source.online = false
        assertFalse(updater.refresh().reachedSource)
        assertTrue(currencies.exchangeRates.first().isEmpty())
    }
}
