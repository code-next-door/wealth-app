package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A category switched off in Settings (e.g. transfers to a broker) leaves the
 * Spending totals; its expenses sit in one line that opens and closes.
 */
@RunWith(AndroidJUnit4::class)
class NotCountedSpendingFlowTest : UiTest() {

    @Test
    fun switchingACategoryOffMovesItsExpensesOutOfSpending() {
        // Its own category, so the other tests' spending is unaffected.
        val category = "Broker ${System.nanoTime()}"
        val description = "Transfer ${System.nanoTime()}"
        runBlocking {
            val catalog = container.catalogRepository
            catalog.addExpenseCategory(category)
            val id = catalog.expenseCategories.first().single { it.name == category }.id
            container.expenseRepository.save(Expense(0, LocalDate.now(), 2_000_00, "CHF", description, id, true, null, null))
        }

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Expense categories").performScrollTo().tap()
        val switch = "Counts as spending: $category"
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(switch))
        rule.onNodeWithContentDescription(switch).assertIsOn().tap()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription(switch) and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        Espresso.pressBack()
        waitForText("Expense categories")
        Espresso.pressBack()

        openTab("Spending")
        // The expense is only in the closed "not counted" line, not in the day list.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Not counted as spending"))
        check(!isShown(description))
        rule.onNodeWithText("Not counted as spending").tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(description))
        waitForText(description)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Not counted as spending"))
        rule.onNodeWithText("Not counted as spending").tap()
        rule.waitUntil(10_000) { !isShown(description) }
    }
}
