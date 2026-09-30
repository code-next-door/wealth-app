package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Money in: added as Received, shown under Income with what was saved; income categories in Settings. */
@RunWith(AndroidJUnit4::class)
class IncomeFlowTest : UiTest() {

    private fun saved(description: String) = runBlocking {
        container.expenseRepository.expensesBetween(LocalDate.now().minusDays(1), LocalDate.now()).first().filter { it.description == description }
    }

    @Test
    fun aReceivedAmountIsIncomeWithWhatWasSaved() {
        val description = "SALARY ${System.nanoTime() % 100000}"
        openTab("Spending")
        rule.onNodeWithContentDescription("Add expense").performClick()
        typeInto("Description", description, substring = true)
        typeInto("Amount", "5000")
        rule.onNodeWithText("Received").tap()
        // Money in: the salary rule applies and picks the income category.
        waitForText("Picked by the rule “SALARY”")
        waitForText("Salary · income")
        rule.onNodeWithText("Save").tap()
        rule.waitUntil(10_000) { saved(description).isNotEmpty() }
        assertEquals(-5_000_00L, saved(description).single().amountMinor)

        // The month shows income and what was saved; the Income card lists it.
        waitForText("Saved")
        // The Income card (the tile above says "Income" too, but isn't a button).
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Income") and hasClickAction())
        rule.onNode(hasText("Income") and hasClickAction()).tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(description))
        rule.onNodeWithText(description).tap()
        waitForText("Edit expense")
        waitForText("5000", substring = true) // the amount, without a sign
    }

    @Test
    fun anIncomeCategoryIsAddedToTheIncomeSection() {
        val name = "Side job ${System.nanoTime() % 100000}"
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Expense categories").performScrollTo().tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Income"))
        rule.onNodeWithContentDescription("Add category").performClick()
        typeInto("Name", name)
        rule.onNode(hasText("Income") and isSelectable()).tap() // the section choice in the dialog
        rule.onNodeWithText("Add").tap()
        rule.waitUntil(10_000) {
            runBlocking { container.catalogRepository.expenseCategories.first().any { it.name == name } }
        }
        assertTrue(runBlocking { container.catalogRepository.expenseCategories.first().single { it.name == name }.isIncome })
    }
}
