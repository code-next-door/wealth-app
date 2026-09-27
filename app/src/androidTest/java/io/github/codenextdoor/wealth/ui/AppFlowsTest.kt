package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.security.LockDelay
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows on a device, through the real UI. The test runner gives
 * the app an in-memory database, so real data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowsTest : UiTest() {

    // Flaky (~1 in 7 runs): after Save, the account list sometimes never appears.
    // Not yet diagnosed; the screen is logged under tag "UiTest" when it fails.
    @Ignore("Flaky; see CLAUDE.md > Testing > Known issues")
    @Test
    fun addAnAccountAndSeeItOnTheDashboard() {
        val name = "Flow cash ${System.nanoTime() % 10000}"
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        typeInto("Account name", name, substring = true)
        rule.onNodeWithText("Type").performClick()
        // Far down the list: scroll the menu to it, as a person would.
        rule.onNodeWithText("Cash · General").performScrollTo().performClick()
        rule.waitForIdle()
        check(rule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1) { "type menu still open" }
        typeInto("Balance", "1234.50")
        // The keyboard can cover the bottom of the form; scroll to Save as a person would.
        rule.onNodeWithText("Save").performScrollTo().performClick()

        // Back on the list (the name alone would also match the form's text field).
        waitForText("Assets")
        rule.onNodeWithText(name).assertIsDisplayed()
        openTab("Overview")
        waitForText("Net worth")
        rule.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test
    fun emptyAccountFormShowsWhatIsMissing() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        rule.onNodeWithText("Save").performScrollTo().performClick()
        waitForText("Required")
    }

    @Test
    fun addingACurrencyValidatesTheCode() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Currencies & exchange rates").performClick()
        rule.onNodeWithContentDescription("Add currency").performClick()
        typeInto("Currency code", "XYZ", substring = true)
        rule.onNodeWithText("Add").performClick()
        // Android itself accepts any three letters as a currency; the app must not.
        waitForText("Not a known ISO currency code")
    }

    @Test
    fun expenseFormPicksCategoryFromRules() {
        openTab("Spending")
        rule.onNodeWithContentDescription("Add expense").performClick()
        typeInto("Description", "TWINT *MIGROS ZURICH", substring = true)
        waitForText("Picked by the rule “MIGROS”")
        rule.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun floatingButtonsHaveLabelsForScreenReaders() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").assertIsDisplayed()
        openTab("Spending")
        rule.onNodeWithContentDescription("Import statement").assertIsDisplayed()
    }

    @Test
    fun lockScreenNeedsTheRightPin() {
        val lock = container.appLock
        rule.runOnIdle {
            lock.setPin("2468")
            lock.setDelay(LockDelay.IMMEDIATELY)
            lock.onAppBackgrounded()
            lock.onAppForegrounded()
        }
        waitForText("Wealth is locked")
        // The app underneath is hidden while locked.
        rule.onAllNodes(hasText("Overview")).fetchSemanticsNodes().let { check(it.isEmpty()) { "content visible behind the lock" } }

        listOf("1", "1", "1", "1").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithText("OK").performClick()
        waitForText("Wrong PIN. Try again.")

        listOf("2", "4", "6", "8").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithText("OK").performClick()
        waitForText("Overview")
    }
}
