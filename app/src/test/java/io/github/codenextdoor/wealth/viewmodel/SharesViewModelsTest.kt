package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.accounts.AccountsViewModel
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SharesViewModelsTest : DatabaseTest() {

    private val shares by lazy { ShareRepository(db) }

    private fun addSharesAccount(units: String, cashMinor: Long) = runBlocking {
        val type = catalog.accountTypes.first().single { it.holdsShares }
        accounts.save(
            Account(0, "Stock plan", type.id, "USD", null, cashMinor, Instant.EPOCH, "Morgan Stanley", null, shareSymbol = "GOOG", units = BigDecimal(units)),
            balanceDate = today,
            recordBalance = true,
        )
    }

    @Test
    fun sharesAccountIsListedAtSharesTimesPrice() = runBlocking {
        setRate("USD", "CHF", "0.9")
        shares.setPrice("GOOG", today, BigDecimal("150"))
        addSharesAccount("10", 50_00)
        val state = AccountsViewModel(accounts, catalog, currencies, shares, HouseRepository(db, accounts)).uiState.await { !it.isLoading && it.assets.isNotEmpty() }
        val row = state.assets.single()
        assertTrue(row.balanceText, row.balanceText.contains("1,550")) // 10 × 150 + 50
        assertEquals("10 GOOG × $150.00", row.sharesText)
        assertTrue(row.baseValueText!!.contains("1,395"))
    }

    @Test
    fun sharesWithoutAPriceSaySo() = runBlocking {
        addSharesAccount("10", 0)
        val row = AccountsViewModel(accounts, catalog, currencies, shares, HouseRepository(db, accounts)).uiState.await { !it.isLoading && it.assets.isNotEmpty() }.assets.single()
        assertEquals("GOOG", row.missingPriceFor)
        assertNull(row.baseValueText)
    }

    @Test
    fun grantsShowWhatIsStillUnvestedAndTheNextVest() = runBlocking {
        setRate("USD", "CHF", "0.9")
        shares.setPrice("GOOG", today, BigDecimal("100"))
        shares.saveGrant(Grant(0, "New hire", "GOOG", "USD", today.minusMonths(13), BigDecimal("48"), today.minusMonths(12).minusDays(3), 48, 1, 0, null))
        val state = AccountsViewModel(accounts, catalog, currencies, shares, HouseRepository(db, accounts)).uiState.await { it.grants.isNotEmpty() }
        val grant = state.grants.single()
        assertEquals("New hire", grant.name)
        assertEquals(listOf("36", "48", "GOOG"), listOf(grant.unvestedUnits, grant.totalUnits, grant.symbol))
        assertTrue(grant.valueText!!, grant.valueText!!.contains("3,240")) // 36 × 100 × 0.9, in CHF
        assertEquals("1", grant.nextVestUnits)
        assertTrue(grant.nextVestDate!!.isAfter(today))
        assertTrue(state.unvestedTotalText!!.contains("3,240"))
    }

    @Test
    fun housesLiveInTheirOwnTabAndTheirLoanIsMarkedWhenLeftOut() = runBlocking {
        val houses = HouseRepository(db, accounts)
        val loan = addAccount("Home loan", typeSeedKey = "loan", currency = "INR", balanceMinor = 20_00_000_00, countrySeedKey = "in")
        houses.save(HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today.minusYears(1), BigDecimal("10"), loan))
        val vm = AccountsViewModel(accounts, catalog, currencies, shares, houses)
        val state = vm.uiState.await { !it.isLoading && it.liabilities.isNotEmpty() }
        assertTrue(state.assets.none { it.name == "Flat" })
        assertTrue(state.liabilities.none { it.notInNetWorth })

        houses.setInNetWorth(false)
        val apart = vm.uiState.await { s -> s.liabilities.any { it.notInNetWorth } }
        assertTrue(apart.liabilities.single { it.name == "Home loan" }.notInNetWorth)
        assertEquals("0", apart.liabilitiesTotalText.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" })
    }
}
