package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ShareRepositoryTest : DatabaseTest() {

    private val shares by lazy { ShareRepository(db) }

    private fun book() = runBlocking { shares.prices.first() }

    @Test
    fun stockPlanAccountTypeIsSeeded() = runBlocking {
        val type = catalog.accountTypes.first().single { it.holdsShares }
        assertEquals(typeId("stock_plan"), type.id)
    }

    @Test
    fun downloadedPricesNeverReplaceATypedOne() = runBlocking {
        shares.setPrice("GOOG", today, BigDecimal("150"))
        val saved = shares.saveFetchedPrices("GOOG", mapOf(today to BigDecimal("155"), today.minusDays(1) to BigDecimal("149")))
        assertEquals(1, saved)
        assertEquals(0, BigDecimal("150").compareTo(book().priceAt("GOOG", today)))
        assertFalse(book().pointAt("GOOG", today)!!.fetched)
        assertTrue(book().pointAt("GOOG", today.minusDays(1))!!.fetched)
        assertEquals(today.minusDays(1), shares.latestFetchedDay("GOOG"))

        shares.setPrice("GOOG", today, BigDecimal("155"), fetched = true) // "Use downloaded price"
        assertTrue(book().pointAt("GOOG", today)!!.fetched)
    }

    @Test
    fun sharesAccountKeepsSharesPerEntryAndTheLatestOnTheAccount() = runBlocking {
        val type = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", type.id, "USD", null, 12_34, Instant.EPOCH, "Morgan Stanley", null, shareSymbol = "GOOG", units = BigDecimal("10.5")),
            balanceDate = today.minusMonths(3),
            recordBalance = true,
        )
        val id = accounts.accounts.first().single().id
        accounts.addHistoryEntry(id, today, 50_00, units = BigDecimal("12.25"))
        val account = accounts.get(id)!!
        assertEquals("GOOG", account.shareSymbol)
        assertEquals(0, BigDecimal("12.25").compareTo(account.units))
        assertEquals(50_00L, account.balanceMinor)
        val history = accounts.observeHistory(id).first()
        assertEquals(listOf(BigDecimal("12.25"), BigDecimal("10.5")).map { it.stripTrailingZeros() }, history.map { it.units!!.stripTrailingZeros() })
    }

    @Test
    fun grantsAreSavedAndBlockDeletingTheirCurrency() = runBlocking {
        val grant = Grant(0, "New hire", "GOOG", "USD", today, BigDecimal("100"), today, 48, 1, 0, null)
        val id = shares.saveGrant(grant)
        assertEquals(grant.copy(id = id), shares.grants.first().single())
        assertFalse(currencies.deleteCurrency("USD"))
        shares.deleteGrant(id)
        assertTrue(shares.grants.first().isEmpty())
        assertTrue(currencies.deleteCurrency("USD"))
    }
}
