package io.github.codenextdoor.wealth.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.ui.tour.Tour
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeViewModelTest : DatabaseTest() {

    private val onboarding by lazy { OnboardingPreferences(SettingsStore(context, "onboarding_${System.nanoTime()}")) }
    private val tour = Tour()
    private fun viewModel() = WelcomeViewModel(currencies, onboarding, tour).cancelledAfterTest()

    @Test
    fun showsOnAFreshInstallUntilFinished() {
        val vm = viewModel()
        assertFalse(vm.visible.value) // not settled yet
        onboarding.settle(freshInstall = true)
        vm.visible.await { it }
        vm.finish(showTour = false)
        vm.visible.await { !it }
        assertNull(tour.step.value)
    }

    @Test
    fun showMeAroundStartsTheTour() {
        onboarding.settle(freshInstall = true)
        viewModel().finish(showTour = true)
        assertEquals(0, tour.step.value)
        assertTrue(onboarding.welcomeDone.value)
    }

    @Test
    fun offersEveryCurrencyWithTheAppsOwnFirst() {
        val state = viewModel().uiState.await { it.currencies.size > 100 }
        assertEquals(listOf("CHF", "INR", "USD"), state.currencies.take(3).map { it.code })
        assertEquals(1, state.currencies.count { it.code == "CHF" })
        assertEquals("CHF", state.baseCurrency)
    }

    @Test
    fun choosingANewCurrencyAddsItAndMakesItTheBase() = runBlocking {
        viewModel().chooseBaseCurrency("EUR")
        eventually { runBlocking { currencies.baseCurrency.first() == "EUR" } }
        assertTrue(currencies.currencies.first().any { it.code == "EUR" && it.decimals == 2 })
    }
}
