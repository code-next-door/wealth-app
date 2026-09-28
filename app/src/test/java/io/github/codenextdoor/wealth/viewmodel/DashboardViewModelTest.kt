package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.dashboard.BreakdownBy
import io.github.codenextdoor.wealth.dashboard.ChangePeriod
import io.github.codenextdoor.wealth.dashboard.ChartRange
import io.github.codenextdoor.wealth.dashboard.DashboardViewModel
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardViewModelTest : DatabaseTest() {

    private val shares by lazy { ShareRepository(db) }
    private val houses by lazy { HouseRepository(db, accounts) }
    private val vm by lazy { DashboardViewModel(accounts, catalog, currencies, shares, houses).cancelledAfterTest() }

    @Test
    fun aHouseCountsAtItsEstimatedValueAndCanBeLeftOutWithItsLoan() = runBlocking {
        setRate("CHF", "INR", "100", today.minusYears(10))
        val loan = addAccount("Home loan", typeSeedKey = "loan", currency = "INR", balanceMinor = 20_00_000_00, countrySeedKey = "in")
        houses.save(
            HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today.minusYears(1), BigDecimal("10"), loan),
        )
        // 1 crore bought a year ago, +10% a year: 1.1 crore = CHF 110,000; minus the loan (CHF 20,000).
        vm.uiState.await { it.housesOutsideText == null && digits(it.netWorthText) == "9000000" }

        houses.setInNetWorth(false)
        val apart = vm.uiState.await { it.housesOutsideText != null }
        assertEquals("000", digits(apart.netWorthText)) // houses and their loan left out: nothing else here
        assertEquals("9000000", digits(apart.housesOutsideText!!)) // shown apart: equity CHF 90,000.00
    }

    @Test
    fun sharesCountAtTheirPriceAndUnvestedStockIsShownApart() = runBlocking {
        setRate("USD", "CHF", "0.5", today.minusYears(1))
        val type = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", type.id, "USD", null, 0, Instant.EPOCH, null, null, shareSymbol = "GOOG", units = BigDecimal("10")),
            balanceDate = today,
            recordBalance = true,
        )
        val noPrice = vm.uiState.await { it.hasAccounts }
        assertEquals(1, noPrice.excludedCount)
        assertEquals(listOf("GOOG"), noPrice.missingRateCurrencies) // a missing price is named like a missing rate

        shares.setPrice("GOOG", today, BigDecimal("200"))
        shares.saveGrant(Grant(0, "New hire", "GOOG", "USD", today, BigDecimal("48"), today, 48, 1, 0, null))
        val state = vm.uiState.await { it.excludedCount == 0 && it.unvestedText != null }
        assertEquals("100000", digits(state.netWorthText)) // 10 × 200 × 0.5 = 1,000.00; the grant isn't included
        assertEquals("480000", digits(state.unvestedText!!)) // 48 × 200 × 0.5 = 4,800.00
    }

    private fun digits(text: String) = text.filter { it.isDigit() }

    @Test
    fun emptyStateWithoutAccounts() {
        val state = vm.uiState.await { !it.isLoading }
        assertFalse(state.hasAccounts)
    }

    @Test
    fun totalsSubtractLiabilitiesAndFlagMissingRates() {
        addAccount("Salary", balanceMinor = 10_000_00)
        addAccount("Card", typeSeedKey = "credit_card", balanceMinor = 1_500_00, countrySeedKey = null)
        addAccount("Dollars", typeSeedKey = "ch_brokerage", currency = "USD")

        val state = vm.uiState.await { it.hasAccounts && it.excludedCount == 1 }
        assertEquals("850000", digits(state.netWorthText))
        assertEquals("1000000", digits(state.assetsText))
        assertEquals("150000", digits(state.liabilitiesText))
        assertEquals(listOf("USD"), state.missingRateCurrencies)
    }

    @Test
    fun historyTrendAndProjectionFromBalanceHistory() {
        // +1000 per month for a year.
        val id = addAccount("Salary", balanceMinor = 12_000_00)
        runBlocking { (1..12).forEach { m -> accounts.addHistoryEntry(id, today.minusMonths(m.toLong()), (12 - m) * 1_000_00L) } }

        val state = vm.uiState.await { it.history.isNotEmpty() && it.forecast.isNotEmpty() }
        assertEquals(12, state.forecast.size) // 1Y view projects 12 months
        assertNotNull(state.trendPerMonth)
        assertTrue(state.trendPerMonth!!.isIncrease)
        // Roughly +1000/month (a straight line through a monthly staircase).
        val perMonth = digits(state.trendPerMonth.amountText).toInt()
        assertTrue("trend was $perMonth", perMonth in 900..1100)
        assertTrue(state.recentChange!!.isIncrease)

        vm.selectRange(ChartRange.SIX_MONTHS)
        assertEquals(6, vm.uiState.await { it.range == ChartRange.SIX_MONTHS }.forecast.size)
    }

    @Test
    fun noTrendWithTooLittleHistory() {
        addAccount("Salary", balanceMinor = 100_00)
        val state = vm.uiState.await { it.hasAccounts }
        assertTrue(state.history.isEmpty())
        assertNull(state.trendPerMonth)
    }

    @Test
    fun breakdownByTypeCountryAndCurrency() {
        setRate("CHF", "INR", "100")
        addAccount("Salary", balanceMinor = 6_000_00)
        addAccount("3a", typeSeedKey = "ch_pillar3a", balanceMinor = 3_000_00)
        addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", balanceMinor = 100_000_00, countrySeedKey = "in")
        addAccount("Card", typeSeedKey = "credit_card", balanceMinor = 999_00, countrySeedKey = null) // not an asset

        val byType = vm.uiState.await { it.slices.size == 3 }.slices
        assertEquals(listOf("Bank account", "Pillar 3a", "NRE account"), byType.map { it.label })
        assertEquals(1f, byType.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)

        vm.selectBreakdown(BreakdownBy.COUNTRY)
        val byCountry = vm.uiState.await { it.breakdownBy == BreakdownBy.COUNTRY }.slices
        assertEquals(listOf("Switzerland", "India"), byCountry.map { it.label })
        assertEquals("90.0%", byCountry.first().percentText)

        vm.selectBreakdown(BreakdownBy.CURRENCY)
        assertEquals(listOf("CHF", "INR"), vm.uiState.await { it.breakdownBy == BreakdownBy.CURRENCY }.slices.map { it.label })
    }

    @Test
    fun manySmallGroupsFoldIntoOther() {
        listOf("ch_bank", "ch_pillar2", "ch_pillar3a", "ch_brokerage", "real_estate", "gold", "cash").forEachIndexed { i, type ->
            addAccount("A$i", typeSeedKey = type, balanceMinor = (i + 1) * 100_00L)
        }
        val slices = vm.uiState.await { it.slices.isNotEmpty() }.slices
        assertEquals(6, slices.size)
        assertTrue(slices.last().isOther)
    }

    @Test
    fun whatChangedListsAccountsByImpact() {
        val salary = addAccount("Salary", balanceMinor = 5_000_00)
        runBlocking { accounts.addHistoryEntry(salary, today.minusDays(40), 4_000_00) }
        val card = addAccount("Card", typeSeedKey = "credit_card", balanceMinor = 300_00, countrySeedKey = null)
        runBlocking { accounts.addHistoryEntry(card, today.minusDays(40), 100_00) }

        val state = vm.uiState.await { it.movers.size == 2 }
        assertEquals(listOf("Salary", "Card"), state.movers.map { it.name })
        assertTrue(state.movers[0].change.isIncrease)
        assertFalse(state.movers[1].change.isIncrease) // more debt lowers net worth
        assertEquals("80000", digits(state.periodChange!!.amountText))

        vm.selectPeriod(ChangePeriod.YEAR)
        assertEquals(ChangePeriod.YEAR, vm.uiState.await { it.changePeriod == ChangePeriod.YEAR }.changePeriod)
    }
}
