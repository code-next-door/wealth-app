package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** The first-run help, as on a fresh install. */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest : UiTest() {

    private fun freshInstall() {
        rule.runOnUiThread { container.onboarding.startOver() }
        waitForText("Welcome to Wealth")
    }

    @Test
    fun welcomeChoosesTheBaseCurrencyAndSkipGoesStraightIn() {
        freshInstall()
        try {
            rule.onNodeWithText("Show my net worth in").performClick()
            rule.onNodeWithText("EUR", substring = true).performScrollTo().tap()
            rule.waitUntil(10_000) { runBlocking { container.currencyRepository.baseCurrency.first() == "EUR" } }
            rule.onNodeWithText("Skip").performScrollTo().tap()
            waitForText("Overview")
            check(container.onboarding.welcomeDone.value) { "welcome not marked done" }
            check(container.tour.step.value == null) { "Skip started the tour" }
        } finally {
            runBlocking { container.currencyRepository.setBaseCurrency("CHF") } // the other tests expect CHF
        }
    }
}
