package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Splitting one transaction into parts in other categories, and undoing it. */
@RunWith(AndroidJUnit4::class)
class SplitFlowTest : UiTest() {

    private fun saved(name: String): Expense = runBlocking {
        container.expenseRepository.expensesBetween(LocalDate.now().withDayOfMonth(1), LocalDate.now()).first().single { it.description == name }
    }

    private fun openExpense(name: String) {
        openTab("Spending")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onAllNodesWithText(name)[0].tap()
        waitForText("Edit expense")
    }

    @Test
    fun splitAnExpenseAndUndoIt() {
        val name = "Split shop ${System.nanoTime() % 10000}"
        // Uncategorized, so saving asks nothing (no "remember this category?").
        runBlocking { container.expenseRepository.save(Expense(0, LocalDate.now(), 120_00, "CHF", name, null, false, null, null)) }

        openExpense(name)
        rule.onNodeWithText("Split into parts").performScrollTo().tap()
        field("Amount of part 2").performScrollTo()
        typeInto("Amount of part 2", "30")
        waitForText("The rest, CHF", substring = true)
        rule.onNodeWithText("Category of part 2").performScrollTo().tap()
        rule.onNodeWithText("Shopping").performScrollTo().tap()
        rule.onNodeWithText("Save").performScrollTo().tap()

        // Back on the month: one row per part, each saying what it's part of.
        waitForText("part of", substring = true)
        assertEquals(listOf(30_00L), saved(name).parts.map { it.amountMinor })
        assertEquals(120_00L, saved(name).amountMinor)

        openExpense(name)
        rule.onNodeWithText("Don't split").performScrollTo().tap()
        rule.onNodeWithText("Save").performScrollTo().tap()
        waitForText("Spending")
        rule.waitUntil(5_000) { saved(name).parts.isEmpty() }
        assertTrue(saved(name).parts.isEmpty())
    }
}
