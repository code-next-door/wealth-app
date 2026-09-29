package io.github.codenextdoor.wealth.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GettingStartedViewModelTest : DatabaseTest() {

    private val onboarding by lazy { OnboardingPreferences(SettingsStore(context, "onboarding_${System.nanoTime()}")) }
    private val lock by lazy { AppLock(SettingsStore(context, "lock_${System.nanoTime()}")) }
    private fun viewModel() = GettingStartedViewModel(accounts, lock, onboarding).cancelledAfterTest()

    @Test
    fun aFreshInstallShowsItAndStepsTickThemselvesOff() {
        onboarding.settle(freshInstall = true)
        val vm = viewModel()
        vm.uiState.await { it.visible && it.done.isEmpty() }
        addAccount("Salary")
        vm.uiState.await { GettingStartedStep.ADD_ACCOUNT in it.done }
        lock.setPin("4827")
        onboarding.setBackupMade()
        assertEquals(3, vm.uiState.await { it.done.size == 3 }.done.size)
        // A second day's balance on one account: history.
        val old = addAccount("Old", date = today.minusMonths(2))
        runBlocking { accounts.save(accounts.get(old)!!, today, recordBalance = true) }
        vm.uiState.await { !it.visible } // all four done: it goes away
    }

    @Test
    fun hidingItKeepsItHidden() {
        onboarding.settle(freshInstall = true)
        val vm = viewModel()
        vm.uiState.await { it.visible }
        vm.hide()
        vm.uiState.await { !it.visible }
    }

    @Test
    fun someoneUpdatingNeverSeesIt() {
        onboarding.settle(freshInstall = false)
        assertFalse(viewModel().uiState.await { !it.visible }.visible)
    }
}
