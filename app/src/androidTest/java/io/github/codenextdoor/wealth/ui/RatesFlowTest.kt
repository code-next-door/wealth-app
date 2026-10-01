package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Downloaded exchange rates in the UI. The test app's rate source is fake
 * ("1 CHF = 100 INR", see TestRates): nothing goes online.
 */
@RunWith(AndroidJUnit4::class)
class RatesFlowTest : UiTest() {

    @Test
    fun currenciesScreenRefreshesRatesAndSaysTheyWereDownloaded() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Currencies & exchange rates").performClick()
        rule.onNodeWithText("Refresh rates").performClick()
        waitForText("Rates updated", substring = true)
        waitForText("Downloaded rate of", substring = true)
    }

    @Test
    fun accountFormFillsTheRateAndCanGoBackToItAfterTypingOver() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add asset account").performClick()
        rule.onNodeWithText("Currency").performClick()
        rule.onNodeWithText("INR · Indian Rupee").performScrollTo().tap()

        waitForText("Downloaded rate of", substring = true)
        val rate = field("Exchange rate that day", substring = true)
        rate.performScrollTo()
        rate.performTextReplacement("95")
        waitForText("Saving sets this day's rate", substring = true)

        rule.onNodeWithText("Use downloaded rate: 100 INR").performScrollTo().tap()
        waitForText("Downloaded rate of", substring = true)
        check(!isShown("Use downloaded rate", substring = true)) { "button still shown with the downloaded rate in the field" }
    }
}
