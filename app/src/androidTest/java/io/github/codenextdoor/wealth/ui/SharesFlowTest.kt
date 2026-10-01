package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stock grants and accounts holding shares, through the UI. Share prices come
 * from the test app's fake source (GOOG = 150, see TestPrices); nothing goes online.
 */
@RunWith(AndroidJUnit4::class)
class SharesFlowTest : UiTest() {

    @Test
    fun addAStockGrantAndSeeWhatIsUnvested() {
        addBankAccount("Grant test bank ${System.nanoTime() % 10000}") // so the Overview shows its headline
        val name = "Grant ${System.nanoTime() % 10000}"
        openTab("Accounts")
        // Below every account; the list is lazy and grows with other tests' accounts.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add stock grant"))
        rule.onNodeWithText("Add stock grant").tap()
        typeInto("Name, e.g.", name, substring = true)
        typeInto("Share symbol", "GOOG", substring = true)
        typeInto("Total units granted", "48")
        waitForText("48 vests of 1 GOOG") // the preview of the pattern (monthly over 48 months)
        rule.onNodeWithText("Save").performScrollTo().tap()

        // Grants are below every account: scroll the list down to it (rows off screen don't exist yet).
        // Back on the list, still scrolled down to the grants (the top may be off screen).
        waitForText("Stock grants")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        waitForText(name)
        waitForText("48 of 48 GOOG unvested", substring = true)
        openTab("Overview")
        waitForText("Unvested stock, not in net worth", substring = true)
    }

    @Test
    fun sharesAccountDownloadsThePriceAndIsValuedWithIt() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        typeInto("Account name", "Stock plan ${System.nanoTime() % 10000}", substring = true)
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Shares (stock plan) · General").performScrollTo().tap()
        rule.onNodeWithText("Currency").performClick()
        rule.onNodeWithText("USD · US Dollar").performScrollTo().tap()

        typeInto("Share symbol", "GOOG", substring = true)
        typeInto("Number of shares", "10")
        waitForText("Downloaded closing price of", substring = true)
        rule.onNodeWithText("Save").performScrollTo().tap()

        waitForText("10 GOOG × $150.00", timeoutMs = 20_000)
    }
}
