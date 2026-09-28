package io.github.codenextdoor.wealth.ui

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.security.LockDelay
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows on a device, through the real UI. The test runner gives
 * the app an in-memory database, so real data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowsTest : UiTest() {

    // Was flaky (~1 in 7 runs) while Save was clicked by touch: the keyboard moved the
    // form under the touch. tap() uses the click action instead.
    @Test
    fun addAnAccountAndSeeItOnTheDashboard() {
        val name = "Flow cash ${System.nanoTime() % 10000}"
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        typeInto("Account name", name, substring = true)
        rule.onNodeWithText("Type").performClick()
        // Far down the list: scroll the menu to it, as a person would.
        rule.onNodeWithText("Cash · General").performScrollTo().tap()
        rule.waitForIdle()
        check(rule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1) { "type menu still open" }
        typeInto("Balance", "1234.50")
        // The keyboard can cover the bottom of the form; scroll to Save as a person would.
        rule.onNodeWithText("Save").performScrollTo().tap()

        // Back on the list (the name alone would also match the form's text field).
        waitForText("Assets")
        rule.onNodeWithText(name).assertIsDisplayed()
        openTab("Overview")
        waitForText("Net worth")
        rule.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test
    fun anAccountLeftOutOfNetWorthIsMarked() {
        val name = "Joint ${System.nanoTime() % 10000}"
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        typeInto("Account name", name, substring = true)
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Cash · General").performScrollTo().tap()
        rule.waitForIdle()
        typeInto("Balance", "500")
        rule.onNodeWithText("Include in net worth").performScrollTo().tap()
        rule.onNodeWithText("Save").performScrollTo().tap()

        waitForText("Assets")
        waitForText("Not in net worth")
        rule.onNodeWithText(name).assertIsDisplayed()
        openTab("Overview")
        waitForText("Accounts left out of net worth", substring = true)
    }

    @Test
    fun screenshotsAreAllowed() {
        // "Hide content in recent apps" is on by default; it must no longer block screenshots.
        waitForText("Overview")
        val flags = rule.runOnUiThread { rule.activity.window.attributes.flags }
        check(flags and WindowManager.LayoutParams.FLAG_SECURE == 0) { "the window blocks screenshots" }
    }

    @Test
    fun emptyAccountFormShowsWhatIsMissing() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add account").performClick()
        rule.onNodeWithText("Save").performScrollTo().tap()
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
