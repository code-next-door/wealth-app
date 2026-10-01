package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The chart folds small categories into "N more categories"; it opens, and each one filters the list. */
@RunWith(AndroidJUnit4::class)
class SpendingMoreCategoriesTest : UiTest() {

    @Test
    fun theFoldedCategoriesOpenAndEachFiltersTheList() {
        val tag = System.nanoTime() % 100000
        val small = "Tiny $tag"
        runBlocking {
            val catalog = container.catalogRepository
            // Seven big categories and one tiny one: the tiny one is folded, whatever else is spent this month.
            (1..7).forEach { catalog.addExpenseCategory("Big $tag $it") }
            catalog.addExpenseCategory(small)
            val ids = catalog.expenseCategories.first().associate { it.name to it.id }
            (1..7).forEach { i ->
                container.expenseRepository.save(Expense(0, LocalDate.now(), 1_000_000_00L + i, "CHF", "Big spend $tag $i", ids.getValue("Big $tag $i"), true, null, null))
            }
            container.expenseRepository.save(Expense(0, LocalDate.now(), 1, "CHF", "Tiny spend $tag", ids.getValue(small), true, null, null))
        }

        openTab("Spending")
        val list = rule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("more categories", substring = true) and hasClickAction())
        rule.onNode(hasText("more categories", substring = true) and hasClickAction()).tap()
        // Its legend line (with a %), not its expense row further down.
        val legendLine = hasText(small) and hasText("%", substring = true) and hasClickAction()
        list.performScrollToNode(legendLine)
        rule.onNode(legendLine).tap()

        // Filtered to it: its expense is listed, a big one isn't.
        list.performScrollToNode(hasText("Tiny spend $tag"))
        waitForText("Tiny spend $tag")
        check(!isShown("Big spend $tag 1")) { "the list isn't filtered" }
    }
}
