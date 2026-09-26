package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.settings.AddCurrencyResult
import io.github.codenextdoor.wealth.settings.CurrenciesViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CurrenciesViewModelTest : DatabaseTest() {

    private val vm by lazy { CurrenciesViewModel(currencies) }

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
        // USD -> INR only exists via CHF now.
        val usd = vm.uiState.await { it.baseCurrency == "INR" }.rows.single { it.code == "USD" }
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
