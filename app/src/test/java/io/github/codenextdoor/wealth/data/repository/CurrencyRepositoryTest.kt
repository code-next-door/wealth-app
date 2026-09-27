package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Currency
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

@RunWith(AndroidJUnit4::class)
class CurrencyRepositoryTest : DatabaseTest() {

    private fun rate(from: String, to: String, date: java.time.LocalDate = today) =
        runBlocking { currencies.rateBook.first().converterAt(date).rate(from, to) }

    @Test
    fun ratesWorkBothWays() {
        setRate("CHF", "INR", "100")
        assertEquals(0, BigDecimal("100").compareTo(rate("CHF", "INR")))
        assertEquals(0, BigDecimal("0.01").compareTo(rate("INR", "CHF")))
    }

    @Test
    fun enteringTheOtherDirectionSameDayReplacesTheRate() = runBlocking {
        setRate("CHF", "INR", "100")
        setRate("INR", "CHF", "0.0125") // = 80 INR per CHF
        assertEquals(1, currencies.exchangeRates.first().size)
        assertEquals(0, BigDecimal("80").compareTo(rate("CHF", "INR")))
    }

    @Test
    fun ratesOnDifferentDaysFormAHistory() {
        setRate("CHF", "INR", "90", today.minusDays(60))
        setRate("CHF", "INR", "105", today)
        assertEquals(0, BigDecimal("90").compareTo(rate("CHF", "INR", today.minusDays(30))))
        assertEquals(0, BigDecimal("105").compareTo(rate("CHF", "INR", today)))
        // The screen's "current" rate is the latest one.
        val current = runBlocking { currencies.exchangeRates.first().single() }
        assertEquals(0, BigDecimal("105").compareTo(current.rate))
    }

    @Test
    fun addCurrencyAndBaseCurrency() = runBlocking {
        currencies.addCurrency(Currency("EUR", "Euro", 2))
        assertEquals("EUR", currencies.currencies.first().last().code)
        currencies.setBaseCurrency("EUR")
        assertEquals("EUR", currencies.baseCurrency.first())
    }

    @Test
    fun deletingACurrencyDeletesItsRates() = runBlocking {
        setRate("CHF", "USD", "1.1")
        assertTrue(currencies.deleteCurrency("USD"))
        assertTrue(currencies.exchangeRates.first().isEmpty())
        assertNull(rate("USD", "CHF"))
    }

    @Test
    fun currencyInUseCantBeDeleted() = runBlocking {
        addAccount("Dollar account", currency = "USD")
        assertFalse(currencies.deleteCurrency("USD"))
        assertTrue(currencies.currencies.first().any { it.code == "USD" })
    }

    @Test
    fun currencyUsedByAnExpenseCantBeDeleted() = runBlocking {
        expenses.save(expense("Book", 10_00, currency = "INR"))
        assertFalse(currencies.deleteCurrency("INR"))
    }

    private fun point(a: String, b: String, date: java.time.LocalDate) = runBlocking {
        currencies.rateBook.first().pointAt(a, b, date)
    }

    @Test
    fun fetchedRatesAreSavedAndMarked() = runBlocking {
        val saved = currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94.75"), "USD" to BigDecimal("1.1")))
        assertEquals(2, saved)
        assertEquals(0, BigDecimal("94.75").compareTo(rate("CHF", "INR")))
        assertTrue(point("INR", "CHF", today)!!.fetched)
    }

    @Test
    fun aTypedRateIsNeverReplacedByAFetchedOne() = runBlocking {
        setRate("INR", "CHF", "0.01") // typed, the other way round
        assertEquals(0, currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94.75"))))
        assertEquals(0, BigDecimal("100").compareTo(rate("CHF", "INR")))
        assertFalse(point("CHF", "INR", today)!!.fetched)
    }

    @Test
    fun aNewerFetchReplacesAnOlderFetchForTheSameDay() = runBlocking {
        currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94")))
        currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("95")))
        assertEquals(1, currencies.exchangeRates.first().size)
        assertEquals(0, BigDecimal("95").compareTo(rate("CHF", "INR")))
    }

    @Test
    fun typingOverAFetchedRateMakesItTheUsersAndChoosingTheFetchedOneAgainUndoesThat() = runBlocking {
        currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94")))
        setRate("CHF", "INR", "90")
        assertFalse(point("CHF", "INR", today)!!.fetched)
        assertEquals(0, currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94"))))

        // "Use fetched rate" in a form.
        currencies.setRate("CHF", "INR", BigDecimal("94"), today, fetched = true)
        assertTrue(point("CHF", "INR", today)!!.fetched)
        assertEquals(1, currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("95"))))
    }

    @Test
    fun fetchedRatesForUnknownCurrenciesAreIgnored() = runBlocking {
        assertEquals(1, currencies.saveFetchedRates("CHF", today, mapOf("INR" to BigDecimal("94"), "EUR" to BigDecimal("0.95"))))
    }
}
