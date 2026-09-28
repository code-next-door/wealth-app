package io.github.codenextdoor.wealth.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.rates.Quote
import io.github.codenextdoor.wealth.data.rates.RateSource
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.house.HouseEditViewModel
import io.github.codenextdoor.wealth.house.HouseListViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HouseViewModelsTest : DatabaseTest() {

    private val houses by lazy { HouseRepository(db, accounts) }
    private val noRates = object : RateSource {
        override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate) = emptyMap<String, Quote>()
    }

    private fun editor(accountId: Long? = null) = HouseEditViewModel(
        SavedStateHandle(if (accountId == null) emptyMap() else mapOf(HouseEditViewModel.ARG_ACCOUNT_ID to accountId)),
        houses, accounts, catalog, currencies, RateUpdater(currencies, accounts, noRates) { today },
    ).cancelledAfterTest().also { vm -> vm.data.await { vm.uiState(it).isReady } }

    @Test
    fun listShowsTodaysEstimateGainAndEquity() = runBlocking {
        setRate("CHF", "INR", "100", today.minusYears(10))
        val loan = addAccount("Home loan", typeSeedKey = "loan", currency = "INR", balanceMinor = 20_00_000_00, countrySeedKey = "in")
        houses.save(HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today.minusYears(1), BigDecimal("10"), loan))
        val state = HouseListViewModel(houses, accounts, currencies, todayFlow).cancelledAfterTest().uiState.await { it.houses.isNotEmpty() }
        assertTrue(state.inNetWorth)
        val house = state.houses.single()
        assertEquals("Flat", house.name)
        assertEquals("1100000000", house.valueText.filter { it.isDigit() }) // 1.1 crore = 1,10,00,000.00
        assertEquals("10", house.yearlyGainPercent) // +10% a year
        assertEquals("Home loan", house.loanName)
        assertEquals("900000000", house.equityText!!.filter { it.isDigit() }) // minus the 20 lakh loan
        assertTrue(house.baseValueText!!.filter { it.isDigit() }.startsWith("110000"))
    }

    @Test
    fun theEstimateMovesOnWithTheDate() = runBlocking<Unit> {
        houses.save(HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today.minusYears(1), BigDecimal("10"), null))
        val vm = HouseListViewModel(houses, accounts, currencies, todayFlow).cancelledAfterTest()
        vm.uiState.await { it.houses.singleOrNull()?.valueText?.filter(Char::isDigit) == "1100000000" }
        todayFlow.value = today.plusYears(1)
        vm.uiState.await { it.houses.single().valueText.filter(Char::isDigit) == "1210000000" }
    }

    @Test
    fun theSwitchLeavesHousesOutOfNetWorth() = runBlocking {
        val vm = HouseListViewModel(houses, accounts, currencies, todayFlow).cancelledAfterTest()
        vm.setInNetWorth(false)
        assertFalse(vm.uiState.await { !it.inNetWorth }.inNetWorth)
        assertFalse(houses.inNetWorth.first())
    }

    @Test
    fun newHouseIsValidatedAndSaved() = runBlocking {
        val vm = editor()
        assertEquals("CHF", vm.uiState().currencyCode)
        vm.save()
        assertTrue(vm.uiState().nameError && vm.uiState().priceError)
        vm.fields.name.setTextAndPlaceCursorAtEnd("Bengaluru flat")
        vm.fields.price.setTextAndPlaceCursorAtEnd("8000000")
        vm.fields.growth.setTextAndPlaceCursorAtEnd("abc")
        vm.onCurrencyChange("INR")
        vm.onPurchaseDateChange(LocalDate.of(2020, 6, 1))
        vm.save()
        assertTrue(vm.uiState().growthError)
        vm.fields.growth.setTextAndPlaceCursorAtEnd("6.5")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        val house = houses.houses.first().single()
        assertEquals(0, BigDecimal("6.5").compareTo(house.growthPercent))
        assertEquals(80_00_000_00L, house.purchasePriceMinor)
        assertEquals("INR", accounts.get(house.accountId)!!.currencyCode)
    }

    @Test
    fun anExistingRealEstateAccountCanBecomeAHouse() = runBlocking {
        val flat = addAccount("Old flat", typeSeedKey = "real_estate", currency = "INR", balanceMinor = 90_00_000_00, countrySeedKey = "in")
        val vm = editor()
        assertEquals(listOf(flat), vm.uiState().convertible.map { it.id })
        vm.onConvert(flat) // takes its name, currency and country
        assertEquals("Old flat", vm.fields.name.text.toString())
        assertEquals("INR", vm.uiState().currencyCode)
        vm.fields.price.setTextAndPlaceCursorAtEnd("7000000")
        vm.onPurchaseDateChange(today.minusYears(3))
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertEquals(flat, houses.houses.first().single().accountId)
        assertEquals(2, accounts.observeHistory(flat).first().size) // its old value kept, plus the purchase
    }

    @Test
    fun valuationsAreAddedAndRemovedButNotThePurchase() = runBlocking {
        val id = houses.save(HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today.minusYears(2), BigDecimal("7"), null))
        val vm = editor(id)
        assertEquals("Flat", vm.fields.name.text.toString())
        vm.addValuation(today.minusMonths(1), 1_30_00_000_00, null)
        val withValuation = vm.data.await { vm.uiState(it).valuations.size == 2 }.let { vm.uiState(it) }
        assertTrue(withValuation.valuations.first { it.date == today.minusYears(2) }.isPurchase)
        val valuation = withValuation.valuations.single { !it.isPurchase }
        vm.deleteValuation(valuation.id)
        assertTrue(vm.data.await { vm.uiState(it).valuations.size == 1 }.let { vm.uiState(it).valuations.single().isPurchase })
    }
}
