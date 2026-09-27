package io.github.codenextdoor.wealth.data.rates

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Grant
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
import java.time.Instant
import java.time.LocalDate

/** Invented price data; nothing goes to the internet. */
@RunWith(AndroidJUnit4::class)
class SharePricesTest : DatabaseTest() {

    @Test
    fun yahooClosesAreReadOnTheExchangesDaysSkippingGaps() = runBlocking {
        // 2025-03-28 and 2025-03-31, 09:30 New York time; one day without a close.
        val body = """
            {"chart":{"result":[{"meta":{"currency":"USD","symbol":"GOOG","exchangeTimezoneName":"America/New_York"},
            "timestamp":[1743168600,1743255000,1743427800],
            "indicators":{"quote":[{"close":[155.3699951171875,null,156.22999572753906]}]}}],"error":null}}
        """.trimIndent()
        val asked = mutableListOf<String>()
        val source = YahooPriceSource { url -> asked += url; body }
        val closes = source.closes("GOOG", LocalDate.of(2025, 3, 28), LocalDate.of(2025, 3, 31))
        assertEquals(
            mapOf(LocalDate.of(2025, 3, 28) to BigDecimal("155.37"), LocalDate.of(2025, 3, 31) to BigDecimal("156.23")),
            closes,
        )
        assertEquals("https://query1.finance.yahoo.com/v8/finance/chart/GOOG?period1=1743120000&period2=1743465600&interval=1d", asked.single())
        assertTrue(YahooPriceSource { null }.closes("GOOG", LocalDate.of(2025, 3, 28), LocalDate.of(2025, 3, 31)).isEmpty())
        assertTrue(YahooPriceSource { """{"chart":{"result":null,"error":{"code":"Not Found"}}}""" }.closes("NOPE", LocalDate.of(2025, 3, 28), LocalDate.of(2025, 3, 31)).isEmpty())
    }

    /** GOOG costs 100 + day of month, every day. */
    private class FakePrices(var online: Boolean = true) : PriceSource {
        val asked = mutableListOf<Triple<String, LocalDate, LocalDate>>()
        override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal> {
            asked += Triple(symbol, from, to)
            if (!online || symbol != "GOOG") return emptyMap()
            return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { BigDecimal(100 + it.dayOfMonth) }
        }
    }

    private val shares by lazy { ShareRepository(db) }
    private val source = FakePrices()
    private val updater by lazy { PriceUpdater(shares, accounts, source) { today } }
    private fun price(day: LocalDate) = runBlocking { shares.prices.first().priceAt("GOOG", day) }

    private fun addSharesAccount(since: LocalDate) = runBlocking {
        val type = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", type.id, "USD", null, 0, Instant.EPOCH, null, null, shareSymbol = "GOOG", units = BigDecimal("10")),
            balanceDate = since,
            recordBalance = true,
        )
    }

    @Test
    fun lookUpGivesTheLastCloseOnOrBeforeADayAndSavesIt() = runBlocking {
        val found = updater.lookUp("GOOG", today)!!
        assertEquals(today, found.date)
        assertEquals(0, BigDecimal(100 + today.dayOfMonth).compareTo(found.price))
        assertEquals(0, found.price.compareTo(price(today)))
        assertNull(updater.lookUp("NOPE", today))
    }

    @Test
    fun refreshDownloadsFromTheFirstHoldingOnlyOnceAndKeepsTypedPrices() = runBlocking {
        val since = today.minusDays(20)
        addSharesAccount(since)
        shares.setPrice("GOOG", today.minusDays(5), BigDecimal("1")) // typed
        assertTrue(updater.refresh())
        assertEquals(Triple("GOOG", since, today), source.asked.single())
        assertEquals(0, BigDecimal(100 + since.dayOfMonth).compareTo(price(since)))
        assertEquals(0, BigDecimal.ONE.compareTo(price(today.minusDays(5))))

        // Next time only newer days are asked for; nothing when up to date.
        source.asked.clear()
        updater.refresh()
        assertTrue(source.asked.isEmpty())
    }

    @Test
    fun refreshCoversGrantsAndOlderHistoryAddedLater() = runBlocking {
        shares.saveGrant(Grant(0, "Refresh", "GOOG", "USD", today, BigDecimal("10"), today, 12, 1, 0, null))
        updater.refresh()
        assertEquals(today.minusDays(7), source.asked.single().second) // a grant alone: the last week

        source.asked.clear()
        addSharesAccount(today.minusDays(30))
        updater.refresh()
        assertEquals(Triple("GOOG", today.minusDays(30), today.minusDays(8)), source.asked.single())
    }

    @Test
    fun refreshOfflineSaysSo() = runBlocking {
        addSharesAccount(today)
        source.online = false
        assertFalse(updater.refresh())
    }
}
