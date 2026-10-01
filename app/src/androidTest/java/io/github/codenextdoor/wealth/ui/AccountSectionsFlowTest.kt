package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Each Accounts section's "+" adds its own kind: assets, liabilities, stock grants. */
@RunWith(AndroidJUnit4::class)
class AccountSectionsFlowTest : UiTest() {

    @Test
    fun theLiabilitiesPlusOffersLiabilityTypesOnly() {
        openTab("Accounts")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Add liability"))
        rule.onNodeWithContentDescription("Add liability").tap()
        rule.onNodeWithText("Type").performClick()
        waitForText("Credit card", substring = true)
        check(!isShown("Bank account · Switzerland")) { "an asset type is offered for a liability" }
    }

    @Test
    fun theAssetsPlusOffersAssetTypesOnly() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add asset account").performClick()
        rule.onNodeWithText("Type").performClick()
        waitForText("Bank account · Switzerland")
        check(!isShown("Credit card", substring = true)) { "a liability type is offered for an asset" }
        Espresso.pressBack()
    }

    @Test
    fun theStockGrantsPlusOpensTheGrantForm() {
        openTab("Accounts")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Add stock grant"))
        rule.onNodeWithContentDescription("Add stock grant").tap()
        waitForText("Total units granted")
    }
}
