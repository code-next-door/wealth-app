package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Spending › Regular payments: found from the transactions, hidden and shown again, and into Search. */
@RunWith(AndroidJUnit4::class)
class RegularPaymentsFlowTest : ScreenJourneys() {

    @Test
    fun aMonthlyChargeIsListedCanBeHiddenAndOpensSearch() {
        val shop = addRegularPayment()
        openRegularPayments()
        waitForText("Regular payments")
        waitForText(shop)
        waitForText("monthly", substring = true)

        // Not a regular payment: moves to Hidden, and back again.
        rule.onNode(hasContentDescription("Not a regular payment") and hasAncestorWithText(shop)).tap()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(shop)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Hidden (", substring = true).tap()
        waitForText(shop)
        rule.onNode(hasContentDescription("Show again") and hasAncestorWithText(shop)).tap()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Hidden (", substring = true)).fetchSemanticsNodes().isEmpty() || isShown(shop) }

        // Its row opens Search for the shop.
        rule.onNodeWithText(shop).tap()
        waitForText("Money out, last 12 months")
        waitForText("$shop 4521 ZURICH")
    }

    private fun hasAncestorWithText(text: String) = androidx.compose.ui.test.hasAnyAncestor(hasText(text))
}
