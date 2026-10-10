package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.ReminderPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.reminders.RemindersViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Overview's "Not updated for a while" card and Settings › Reminders. */
@RunWith(AndroidJUnit4::class)
class RemindersViewModelTest : DatabaseTest() {

    private val prefs by lazy { ReminderPreferences(SettingsStore(context, "reminders_${System.nanoTime()}")) }
    private val calculated = MutableStateFlow(emptySet<Long>())

    private fun viewModel() = RemindersViewModel(accounts.accounts, accounts.balanceEntries, calculated, prefs, todayFlow).cancelledAfterTest()

    @Test
    fun anOldBalanceShowsTheCardWithItsDate() {
        addAccount("Savings", date = today.minusDays(50))
        addAccount("Current", date = today.minusDays(2))
        val state = viewModel().state.await { !it.isLoading }
        assertTrue(state.showCard)
        assertEquals(listOf("Savings"), state.stale.map { it.accountName })
        assertEquals(today.minusDays(50), state.stale.single().lastUpdated)
    }

    @Test
    fun notNowHidesTheCardForAWeek() {
        addAccount("Savings", date = today.minusDays(50))
        val vm = viewModel()
        vm.state.await { it.showCard }
        vm.notNow()
        assertFalse(vm.state.await { !it.showCard }.showCard)
        assertEquals(today.plusDays(7), prefs.snoozedUntil.value)
        // A week later it's back.
        todayFlow.value = today.plusDays(7)
        vm.state.await { it.showCard }
    }

    @Test
    fun switchedOffShowsNoCard() {
        addAccount("Savings", date = today.minusDays(50))
        val vm = viewModel()
        vm.state.await { it.showCard }
        vm.setEnabled(false)
        assertFalse(vm.state.await { !it.enabled }.showCard)
    }

    @Test
    fun moreDaysAndCalculatedAccountsMeanFewerReminders() {
        val pension = addAccount("Pension", date = today.minusDays(50))
        addAccount("Savings", date = today.minusDays(50))
        val vm = viewModel()
        vm.state.await { it.stale.size == 2 }
        calculated.value = setOf(pension)
        assertEquals(listOf("Savings"), vm.state.await { it.stale.size == 1 }.stale.map { it.accountName })
        vm.setDays(60)
        assertEquals(60, vm.state.await { it.stale.isEmpty() }.days)
    }

    @Test
    fun theReviewCardShowsInTheFirstWeekForLastMonthUntilHidden() {
        addAccount("Savings", date = today.minusMonths(2))
        val third = java.time.LocalDate.of(2026, 10, 3)
        todayFlow.value = third
        val vm = viewModel()
        val first = vm.state.await { !it.isLoading && it.showReviewCard }
        assertEquals(java.time.YearMonth.of(2026, 9), first.reviewMonth)

        todayFlow.value = java.time.LocalDate.of(2026, 10, 8) // second week: gone
        vm.state.await { !it.showReviewCard }
        todayFlow.value = third
        vm.state.await { it.showReviewCard }

        vm.hideReview()
        vm.state.await { !it.showReviewCard }
        todayFlow.value = java.time.LocalDate.of(2026, 11, 2) // next month's review: shown again
        vm.state.await { it.showReviewCard && it.reviewMonth == java.time.YearMonth.of(2026, 10) }

        vm.setReviewEnabled(false)
        assertFalse(vm.state.await { !it.reviewEnabled }.showReviewCard)
    }

    @Test
    fun noReviewCardWithoutAccounts() {
        todayFlow.value = java.time.LocalDate.of(2026, 10, 3)
        assertFalse(viewModel().state.await { !it.isLoading }.showReviewCard)
    }
}
