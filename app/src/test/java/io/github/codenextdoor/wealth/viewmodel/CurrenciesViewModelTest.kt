package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.settings.AddCurrencyResult
import io.github.codenextdoor.wealth.settings.CurrenciesViewModel
import io.github.codenextdoor.wealth.settings.RatesRefresh
import io.github.codenextdoor.wealth.data.rates.Quote
import io.github.codenextdoor.wealth.data.rates.RateSource
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import java.time.LocalDate
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CurrenciesViewModelTest : DatabaseTest() {

    /** "1 CHF = 100 INR" and "1 CHF = 1.25 USD", unless offline. */
    private val source = object : RateSource {
        var online = true
        override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate) =
            if (!online) emptyMap() else mapOf("INR" to Quote(BigDecimal("100"), date), "USD" to Quote(BigDecimal("1.25"), date)).filterKeys { it in currencies }
    }

    private val vm by lazy { CurrenciesViewModel(currencies, RateUpdater(currencies, accounts, source) { today }) }

    @Test
    fun refreshDownloadsEveryRateAndSaysWhereEachComesFrom() {
        setRate("CHF", "INR", "105") // typed: stays
        vm.refreshRates()
        assertEquals(RatesRefresh.DONE, vm.refresh.await { it != RatesRefresh.RUNNING && it != RatesRefresh.IDLE })
        val rows = vm.uiState.await { s -> s.rows.any { it.code == "USD" && it.rateToBase != null } }.rows
        val usd = rows.single { it.code == "USD" }
        assertEquals(0, BigDecimal("0.8").compareTo(usd.rateToBase))
        assertTrue(usd.rateFetched)
        assertEquals(today, usd.rateDate)
        val inr = rows.single { it.code == "INR" }
        assertFalse(inr.rateFetched)
        assertEquals(0, BigDecimal.ONE.divide(BigDecimal("105"), java.math.MathContext.DECIMAL128).compareTo(inr.rateToBase))
    }

    @Test
    fun refreshOfflineSaysSo() {
        source.online = false
        vm.refreshRates()
        assertEquals(RatesRefresh.OFFLINE, vm.refresh.await { it != RatesRefresh.RUNNING && it != RatesRefresh.IDLE })
    }

    @Test
    fun listsCurrenciesWithBaseFirstAndRates() {
        setRate("CHF", "INR", "105")
        val state = vm.uiState.await { it.rows.size == 3 && it.rows.any { r -> r.rateToBase != null } }
        assertEquals("CHF", state.baseCurrency)
        assertTrue(state.rows.single { it.code == "CHF" }.isBase)
        val inr = state.rows.single { it.code == "INR" }
        assertEquals(0, BigDecimal.ONE.divide(BigDecimal("105"), java.math.MathContext.DECIMAL128).compareTo(inr.rateToBase))
        assertFalse(inr.rateIsDerived)
        assertEquals(null, state.rows.single { it.code == "USD" }.rateToBase)
    }

    @Test
    fun addingCurrenciesIsValidated() {
        vm.uiState.await { it.rows.size == 3 }
        assertEquals(AddCurrencyResult.INVALID_CODE, vm.addCurrency("XYZ"))
        assertEquals(AddCurrencyResult.ALREADY_EXISTS, vm.addCurrency("chf"))
        assertEquals(AddCurrencyResult.ADDED, vm.addCurrency("eur"))
        assertTrue(vm.uiState.await { it.rows.size == 4 }.rows.any { it.code == "EUR" })
    }

    @Test
    fun ratesAreValidatedAndDerivedRatesFlagged() {
        vm.uiState.await { it.rows.size == 3 }
        assertFalse(vm.setRate("USD", "CHF", "abc"))
        assertFalse(vm.setRate("USD", "CHF", "0"))
        assertTrue(vm.setRate("USD", "CHF", "0,80"))
        assertTrue(vm.setRate("INR", "CHF", "0.01"))
        vm.setBaseCurrency("INR")
        // USD -> INR only exists via CHF now. Wait until both saved rates have arrived.
        val usd = vm.uiState.await { s -> s.baseCurrency == "INR" && s.rows.single { it.code == "USD" }.rateToBase != null }
            .rows.single { it.code == "USD" }
        assertTrue(usd.rateIsDerived)
        assertEquals(0, BigDecimal("80").compareTo(usd.rateToBase))
    }

    @Test
    fun baseCurrencyIsNeverDeletedAndUsedCurrenciesAreBlocked() {
        addAccount("Dollars", currency = "USD")
        vm.uiState.await { it.rows.size == 3 }
        vm.deleteCurrency("CHF")
        vm.deleteCurrency("USD")
        assertEquals("USD", vm.deleteBlocked.await { it != null })
        assertEquals(3, vm.uiState.value.rows.size)
    }
}
