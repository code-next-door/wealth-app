package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.accounts.AccountsViewModel
import io.github.codenextdoor.wealth.dashboard.DashboardViewModel
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.PensionRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Pension
import io.github.codenextdoor.wealth.domain.PensionValue
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** A growing pension shows its worked-out value wherever the account's amount counts. */
@RunWith(AndroidJUnit4::class)
class PensionViewModelsTest : DatabaseTest() {

    private val pensions by lazy { PensionRepository(db) }
    private val houses by lazy { HouseRepository(db, accounts) }
    private val certificateDay = today.minusMonths(5).withDayOfMonth(1)

    /** A pillar 2 account: CHF 100'000 on its last certificate, CHF 24'000 a year paid in, 1.25 %. */
    private fun pension(): Pair<Long, Pension> = runBlocking {
        val id = addAccount("Pension fund", typeSeedKey = "ch_pillar2", balanceMinor = 100_000_00, date = certificateDay)
        pensions.save(Pension(id, 24_000_00, BigDecimal("1.25")))
        id to pensions.forAccount(id)!!
    }

    private fun valueToday(pension: Pension) = PensionValue.at(pension, listOf(certificateDay to 100_000_00L), today)

    private fun digits(text: String) = text.filter { it.isDigit() }

    @Test
    fun theRepositoryKeepsTheTerms() = runBlocking {
        val (id, saved) = pension()
        assertEquals(0, BigDecimal("1.25").compareTo(saved.yearlyRate))
        pensions.save(saved.copy(yearlyContributionMinor = 30_000_00))
        assertEquals(30_000_00L, pensions.forAccount(id)!!.yearlyContributionMinor)
        assertEquals(1, pensions.pensions.first().size)
        pensions.delete(id) // its history stays
        assertNull(pensions.forAccount(id))
        assertEquals(1, accounts.observeHistory(id).first().size)
    }

    @Test
    fun theAccountsTabShowsTheValueWorkedOutForToday() {
        val (id, pension) = pension()
        val vm = AccountsViewModel(accounts, catalog, currencies, ShareRepository(db), houses, todayFlow, flowOf(emptyList()), pensions.pensions).cancelledAfterTest()
        val row = vm.uiState.await { s -> s.assets.any { it.id == id && it.isCalculated } }.assets.single { it.id == id }
        val expected = valueToday(pension)
        assertTrue(expected > 100_000_00L) // months of contributions and interest since the certificate
        assertEquals(digits("%d".format(expected)), digits(row.balanceText))
    }

    @Test
    fun netWorthCountsTheWorkedOutValue() = runBlocking {
        val (_, pension) = pension()
        val vm = DashboardViewModel(accounts, catalog, currencies, ShareRepository(db), houses, todayFlow, flowOf(emptyList()), pensions.pensions).cancelledAfterTest()
        val expected = digits("%d".format(valueToday(pension)))
        val state = vm.uiState.await { digits(it.netWorthText) == expected }
        assertEquals(expected, digits(state.netWorthText))
    }

    @Test
    fun theDefaultPensionTypesGrowWithContributions() = runBlocking {
        val types = catalog.accountTypes.first()
        assertEquals(setOf("ch_pillar2", "in_epf", "in_ppf"), types.filter { it.growsWithContributions }.mapNotNull { it.seedKey }.toSet())
    }
}
