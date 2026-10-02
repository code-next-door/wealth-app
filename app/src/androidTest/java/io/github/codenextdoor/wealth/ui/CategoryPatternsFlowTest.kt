package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Settings › Categories and patterns: a category's patterns, and a new one sorting a saved transaction. */
@RunWith(AndroidJUnit4::class)
class CategoryPatternsFlowTest : UiTest() {

    private fun saved(name: String): Expense = runBlocking {
        container.expenseRepository.expensesBetween(LocalDate.now().minusYears(1), LocalDate.now()).first().single { it.description == name }
    }

    @Test
    fun aNewPatternSortsSavedTransactionsAndDeletingItUndoesThat() {
        val tag = System.nanoTime() % 100000
        val name = "ZEBRA$tag SHOP ZURICH"
        runBlocking { container.expenseRepository.save(Expense(0, LocalDate.now().withDayOfMonth(1), 12_00, "CHF", name, null, false, null, null)) }
        val shopping = runBlocking { container.catalogRepository.expenseCategories.first().single { it.seedKey == "shopping" }.id }

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Categories and patterns").performScrollTo().tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Shopping"))
        rule.onNodeWithText("Shopping").tap()
        waitForText("Patterns")
        rule.onNodeWithContentDescription("Add pattern").performClick()
        typeInto("Keyword", "ZEBRA$tag")
        rule.onNodeWithText("Save").tap()

        // The saved transaction moves into Shopping at once (the shared test database may hold
        // other transactions that re-sort too, so not the exact count).
        waitForText("re-categorized", substring = true)
        rule.waitUntil(10_000) { saved(name).categoryId == shopping }
        assertEquals(shopping, saved(name).categoryId)

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ZEBRA$tag"))
        rule.onNodeWithText("ZEBRA$tag").tap()
        rule.onNodeWithText("Delete").tap()
        rule.waitUntil(10_000) { saved(name).categoryId == null }
        assertNull(saved(name).categoryId)
    }
}
