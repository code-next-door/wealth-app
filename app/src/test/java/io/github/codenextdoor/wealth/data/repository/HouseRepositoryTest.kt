package io.github.codenextdoor.wealth.data.repository

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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HouseRepositoryTest : DatabaseTest() {

    private val houses by lazy { HouseRepository(db, accounts) }
    private val bought = LocalDate.of(2020, 6, 1)

    private fun details(accountId: Long = 0, loan: Long? = null) = HouseDetails(
        accountId = accountId,
        name = "Bengaluru flat",
        currencyCode = "INR",
        countryId = countryId("in"),
        purchasePriceMinor = 80_00_000_00,
        purchaseDate = bought,
        growthPercent = BigDecimal("7"),
        loanAccountId = loan,
    )

    @Test
    fun aHouseIsARealEstateAccountWithItsPurchaseAsTheFirstValue() = runBlocking {
        val loan = addAccount("Home loan", typeSeedKey = "loan", currency = "INR", countrySeedKey = "in")
        val id = houses.save(details(loan = loan))
        val house = houses.houses.first().single()
        assertEquals(id, house.accountId)
        assertEquals(loan, house.loanAccountId)
        assertEquals(0, BigDecimal("7").compareTo(house.growthPercent))
        val account = accounts.get(id)!!
        assertEquals(typeId("real_estate"), account.accountTypeId)
        assertEquals("INR", account.currencyCode)
        assertEquals(listOf(bought to 80_00_000_00L), accounts.observeHistory(id).first().map { it.date to it.balanceMinor })
    }

    @Test
    fun changingThePurchaseMovesItsValueAndKeepsValuations() = runBlocking {
        val id = houses.save(details())
        accounts.addHistoryEntry(id, LocalDate.of(2024, 1, 1), 1_20_00_000_00) // a valuation
        houses.save(details(accountId = id).copy(purchaseDate = bought.minusMonths(1), purchasePriceMinor = 79_00_000_00))
        val history = accounts.observeHistory(id).first().map { it.date to it.balanceMinor }.toSet()
        assertEquals(setOf(bought.minusMonths(1) to 79_00_000_00L, LocalDate.of(2024, 1, 1) to 1_20_00_000_00L), history)
    }

    @Test
    fun anExistingRealEstateAccountBecomesAHouseKeepingItsHistory() = runBlocking {
        val existing = addAccount("Flat", typeSeedKey = "real_estate", currency = "INR", balanceMinor = 1_00_00_000_00, date = LocalDate.of(2025, 3, 31), countrySeedKey = "in")
        houses.save(details(accountId = existing))
        val dates = accounts.observeHistory(existing).first().map { it.date }.toSet()
        assertEquals(setOf(bought, LocalDate.of(2025, 3, 31)), dates)
        assertEquals("Bengaluru flat", accounts.get(existing)!!.name)
    }

    @Test
    fun deletingAHouseRemovesItButNotTheLoan() = runBlocking {
        val loan = addAccount("Home loan", typeSeedKey = "loan", currency = "INR", countrySeedKey = "in")
        val id = houses.save(details(loan = loan))
        houses.delete(id)
        assertTrue(houses.houses.first().isEmpty())
        assertNull(accounts.get(id))
        assertEquals("Home loan", accounts.get(loan)!!.name)
    }

    @Test
    fun housesCountInNetWorthUnlessSwitchedOff() = runBlocking {
        assertTrue(houses.inNetWorth.first())
        houses.setInNetWorth(false)
        assertFalse(houses.inNetWorth.first())
    }
}
