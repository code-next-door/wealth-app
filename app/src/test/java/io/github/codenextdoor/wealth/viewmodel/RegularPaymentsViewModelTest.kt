package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.domain.RegularPayments
import io.github.codenextdoor.wealth.regular.RegularPaymentsViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegularPaymentsViewModelTest : DatabaseTest() {

    private fun viewModel() = RegularPaymentsViewModel(expenses, catalog, currencies, todayFlow).cancelledAfterTest()

    /** Three monthly charges ending this month. */
    private fun monthlyCharges(description: String, amountMinor: Long, currency: String = "CHF") = runBlocking {
        (0..2).forEach { expenses.save(expense(description, amountMinor, date = today.minusMonths(it.toLong()), currency = currency)) }
    }

    @Test
    fun listsRegularPaymentsWithAYearInTheBaseCurrency() {
        monthlyCharges("NETFLIX.COM 866-579", 17_90)
        monthlyCharges("SPOTIFY P1234", 12_95)
        val state = viewModel().state.await { it.active.size == 2 }
        assertEquals(listOf("NETFLIX", "SPOTIFY"), state.active.map { it.shop }) // biggest first
        val netflix = state.active.first()
        assertEquals(RegularPayments.Cadence.MONTHLY, netflix.cadence)
        assertTrue(netflix.amountText.withPlainSpaces().endsWith("17.90"))
        assertTrue(netflix.yearlyText!!.withPlainSpaces().endsWith("214.80"))
        assertTrue(state.yearlyTotalText.withPlainSpaces().endsWith("370.20"))
        assertTrue(state.monthlyTotalText.withPlainSpaces().endsWith("30.85"))
    }

    @Test
    fun otherCurrenciesCountAtTodaysRate() {
        setRate("CHF", "INR", "100")
        monthlyCharges("JIO RECHARGE 99", 299_00, currency = "INR")
        val row = viewModel().state.await { it.active.isNotEmpty() }.active.single()
        assertTrue(row.amountText.contains("299"))
        assertTrue(row.yearlyText!!.withPlainSpaces(), row.yearlyText!!.withPlainSpaces().endsWith("35.88"))
    }

    @Test
    fun hidingKeepsItInTheDataAndInBackups() {
        monthlyCharges("COOP PRONTO 4521", 5_00)
        val vm = viewModel()
        vm.state.await { it.active.isNotEmpty() }
        vm.hide("COOP")
        val hidden = vm.state.await { it.hidden.isNotEmpty() }
        assertTrue(hidden.active.isEmpty())
        // Stored as a setting, so a backup carries it.
        assertTrue(runBlocking { BackupRepository(db, context).snapshot() }.settings.any { it.value.contains("COOP") })
        vm.show("COOP")
        assertEquals(listOf("COOP"), vm.state.await { it.hidden.isEmpty() }.active.map { it.shop })
        assertEquals(emptySet<String>(), runBlocking { expenses.hiddenRegularShops.first() })
    }
}
