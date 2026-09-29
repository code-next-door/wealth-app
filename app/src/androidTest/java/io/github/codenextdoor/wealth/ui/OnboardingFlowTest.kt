package io.github.codenextdoor.wealth.ui

import androidx.test.espresso.Espresso
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasText
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

    @Test
    fun showMeAroundWalksThroughSixStopsAndEndsOnTheOverview() {
        freshInstall()
        rule.onNodeWithText("Show me around").performScrollTo().tap()
        for (stop in 1..6) {
            waitForText("$stop of 6")
            // A bubble only shows once its target is on screen: stop 2's needs the Accounts tab's button.
            if (stop == 2) waitForText("Every balance you enter keeps its history", substring = true)
            if (stop == 4) waitForText("Import bank and card statements", substring = true)
            rule.onNodeWithText(if (stop < 6) "Next" else "Done").tap()
        }
        rule.waitUntil(10_000) { container.tour.step.value == null }
        waitForText("Net worth")
        check(rule.onAllNodes(hasText("of 6", substring = true)).fetchSemanticsNodes().isEmpty()) { "bubble still shown" }
    }

    @Test
    fun backEndsTheTourAndATapOnTheDimmedAreaDoesNothing() {
        rule.runOnUiThread { container.tour.start() }
        waitForText("1 of 6")
        rule.onAllNodes(isRoot())[0].performTouchInput { click(Offset(width / 2f, height * 0.25f)) }
        rule.waitForIdle()
        check(container.tour.step.value == 0) { "a tap outside moved the tour" }
        Espresso.pressBack()
        rule.waitUntil(10_000) { container.tour.step.value == null }
        waitForText("Overview")
    }
}
