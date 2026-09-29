package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringFlowTest : UiTest() {

    @Test
    fun aRecurringExpenseStartingTodayIsAddedToSpending() {
        val name = "Rent test ${System.nanoTime() % 10000}"
        openTab("Spending")
        rule.onNodeWithText("Recurring expenses").performScrollTo().tap()
        rule.onNodeWithContentDescription("Add recurring expense").tap()
        typeInto("Description, e.g. Rent", name)
        typeInto("Amount", "1500")
        waitForText("Saving adds 1 expense now", substring = true) // today's
        rule.onNodeWithText("Save").performScrollTo().tap()

        waitForText(name)
        waitForText("Every month", substring = true)
        rule.activity.onBackPressedDispatcher.let { dispatcher -> rule.runOnUiThread { dispatcher.onBackPressed() } }
        // On the Spending tab, as this month's expense (below the year view: scroll to it).
        waitForText("Recurring expenses")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        waitForText(name)
    }
}
