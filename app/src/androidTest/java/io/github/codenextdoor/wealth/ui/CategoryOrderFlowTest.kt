package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Categories keep the order chosen in Settings. Moved with the "Move up" accessibility
 * action (what a screen reader offers instead of dragging): exact, unlike a fake drag.
 */
@RunWith(AndroidJUnit4::class)
class CategoryOrderFlowTest : UiTest() {

    /** Spending categories in order (income ones are their own section). */
    private fun order() = runBlocking { container.catalogRepository.expenseCategories.first().filterNot { it.isIncome }.map { it.name } }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun moveUpPutsACategoryOnePlaceHigher() {
        val category = "Order ${System.nanoTime()}"
        runBlocking { container.catalogRepository.addExpenseCategory(category) } // goes last
        val before = order()
        check(before.last() == category)

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Categories and patterns").performScrollTo().tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(category))
        // The row: its name, and the actions a screen reader offers.
        rule.onNode(hasText(category) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .performCustomAccessibilityActionWithLabel("Move up")

        val expected = before.dropLast(2) + category + before[before.lastIndex - 1]
        rule.waitUntil(10_000) { order() == expected }
    }

    @Test
    fun draggingTheHandleMovesACategoryAndSavesTheOrder() {
        val category = "Drag ${System.nanoTime()}"
        runBlocking { container.catalogRepository.addExpenseCategory(category) } // goes last
        val before = order()

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Categories and patterns").performScrollTo().tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(category))
        val handle = rule.onNodeWithContentDescription("Reorder: $category", useUnmergedTree = true)
        val rowHeight = rule.onNode(hasText(category) and hasClickAction()).fetchSemanticsNode().size.height.toFloat()
        // Hold the handle and drag it up by a bit more than one row, in small steps like a finger.
        handle.performTouchInput {
            down(center)
            repeat(15) { moveBy(Offset(0f, -rowHeight * 1.2f / 15)) }
            up()
        }

        val expected = before.dropLast(2) + category + before[before.lastIndex - 1]
        rule.waitUntil(10_000) { order() == expected }
    }
}
