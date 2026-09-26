package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.security.LockDelay
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows on a device, through the real UI. The test runner gives
 * the app an in-memory database, so real data is never touched.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as WealthApplication).container

    @Before
    fun unlocked() {
        container.appLock.disable()
    }

    @After
    fun tidy() {
        container.appLock.disable()
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun typeInto(label: String, text: String) =
        rule.onNode(hasSetTextAction() and hasText(label, substring = true)).performTextInput(text)

    @Test
    fun addAnAccountAndSeeItOnTheDashboard() {
        val name = "Flow cash ${System.nanoTime() % 10000}"
        rule.onNodeWithText("Accounts").performClick()
        rule.onNodeWithContentDescription("Add account").performClick()
        typeInto("Account name", name)
        rule.onNodeWithText("Type").performClick()
        // Far down the list: scroll the menu to it, as a person would.
        rule.onNodeWithText("Cash · General").performScrollTo().performClick()
        rule.waitForIdle()
        check(rule.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1) { "type menu still open" }
        typeInto("Balance", "1234.50")
        rule.onNodeWithText("Save").performClick()

        // Back on the list (the name alone would also match the form's text field).
        waitForText("Assets")
        rule.onNodeWithText(name).assertIsDisplayed()
        rule.onNodeWithText("Overview").performClick()
        waitForText("Net worth")
        rule.onNodeWithText("Net worth").assertIsDisplayed()
    }

    @Test
    fun emptyAccountFormShowsWhatIsMissing() {
        rule.onNodeWithText("Accounts").performClick()
        rule.onNodeWithContentDescription("Add account").performClick()
        rule.onNodeWithText("Save").performClick()
        waitForText("Required")
    }

    @Test
    fun addingACurrencyValidatesTheCode() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Currencies & exchange rates").performClick()
        rule.onNodeWithContentDescription("Add currency").performClick()
        typeInto("Currency code", "XYZ")
        rule.onNodeWithText("Add").performClick()
        // Android itself accepts any three letters as a currency; the app must not.
        waitForText("Not a known ISO currency code")
    }

    @Test
    fun expenseFormPicksCategoryFromRules() {
        rule.onNodeWithText("Spending").performClick()
        rule.onNodeWithContentDescription("Add expense").performClick()
        typeInto("Description", "TWINT *MIGROS ZURICH")
        waitForText("Picked by the rule “MIGROS”")
        rule.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun floatingButtonsHaveLabelsForScreenReaders() {
        rule.onNodeWithText("Accounts").performClick()
        rule.onNodeWithContentDescription("Add account").assertIsDisplayed()
        rule.onNodeWithText("Spending").performClick()
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
