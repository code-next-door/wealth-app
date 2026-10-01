package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.accounts.AccountsViewModel
import io.github.codenextdoor.wealth.dashboard.DashboardViewModel
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.LoanRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanBalance
import io.github.codenextdoor.wealth.domain.LoanRateChange
import io.github.codenextdoor.wealth.house.HouseListViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** A calculated loan shows its worked-out outstanding everywhere the loan's amount counts. */
@RunWith(AndroidJUnit4::class)
class LoanViewModelsTest : DatabaseTest() {

    private val loans by lazy { LoanRepository(db) }
    private val houses by lazy { HouseRepository(db, accounts) }
    private val firstEmi = today.minusMonths(12)
    private val principal = 20_00_000_00L

    /** A mortgage of ₹20 lakh, a year of EMIs at 8.5%; the principal is its first known balance. */
    private fun mortgage(): Pair<Long, Loan> = runBlocking {
        val id = addAccount("Home loan", typeSeedKey = "mortgage", currency = "INR", balanceMinor = principal, date = firstEmi.minusDays(1), countrySeedKey = "in")
        val loan = Loan(id, principal, firstEmi, 17_356_00L, BigDecimal("8.5"))
        loans.save(loan)
        id to loans.forAccount(id)!!
    }

    private fun outstandingToday(loan: Loan) = LoanBalance.at(loan, listOf(firstEmi.minusDays(1) to principal), today)

    private fun digits(text: String) = text.filter { it.isDigit() }

    @Test
    fun theRepositoryKeepsTermsAndRateChanges() = runBlocking {
        val (id, saved) = mortgage()
        assertEquals(BigDecimal("8.5"), saved.yearlyRate)
        loans.save(saved.copy(rateChanges = listOf(LoanRateChange(0, firstEmi.plusMonths(6), BigDecimal("9"), 18_000_00L))))
        val changed = loans.forAccount(id)!!
        assertEquals(listOf(BigDecimal("9")), changed.rateChanges.map { it.yearlyRate })
        assertEquals(18_000_00L, changed.rateChanges.single().emiMinor)
        assertEquals(1, loans.loans.first().size)

        loans.delete(id) // back to a plain liability: its history stays
        assertNull(loans.forAccount(id))
        assertEquals(1, accounts.observeHistory(id).first().size)
    }

    @Test
    fun theAccountsTabShowsTheCalculatedOutstanding() {
        val (id, loan) = mortgage()
        val vm = AccountsViewModel(accounts, catalog, currencies, ShareRepository(db), houses, todayFlow, loans.loans).cancelledAfterTest()
        val row = vm.uiState.await { s -> s.liabilities.any { it.id == id && it.isCalculated } }.liabilities.single { it.id == id }
        val expected = outstandingToday(loan)
        assertTrue(expected < principal) // a year of EMIs paid some off
        assertEquals(digits("%d".format(expected)), digits(row.balanceText))
    }

    @Test
    fun theHouseEquityUsesTheCalculatedLoan() = runBlocking {
        val (id, loan) = mortgage()
        houses.save(HouseDetails(0, "Flat", "INR", countryId("in"), 1_00_00_000_00, today, BigDecimal("0"), id))
        val vm = HouseListViewModel(houses, accounts, currencies, todayFlow, loans.loans).cancelledAfterTest()
        val house = vm.uiState.await { it.houses.isNotEmpty() }.houses.single()
        assertEquals(digits("%d".format(1_00_00_000_00 - outstandingToday(loan))), digits(house.equityText!!))
    }

    @Test
    fun netWorthCountsTheCalculatedOutstanding() = runBlocking {
        setRate("CHF", "INR", "100", firstEmi.minusYears(1))
        val (_, loan) = mortgage()
        val vm = DashboardViewModel(accounts, catalog, currencies, ShareRepository(db), houses, todayFlow, loans.loans).cancelledAfterTest()
        // Only the loan: net worth is minus its outstanding, ₹ → CHF at 100.
        val expected = BigDecimal(outstandingToday(loan)).divide(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_EVEN)
        val state = vm.uiState.await { digits(it.netWorthText).isNotEmpty() && digits(it.netWorthText) != "000" }
        assertEquals(digits(expected.toPlainString()), digits(state.netWorthText))
    }
}
