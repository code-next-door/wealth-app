package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** Spending › Search finds a transaction from another month and opens it. */
@RunWith(AndroidJUnit4::class)
class SearchFlowTest : UiTest() {

    @Test
    fun findsAnOldTransactionByTextOrAmountAndOpensIt() {
        val tag = "SEARCHTEST${System.nanoTime() % 100000}"
        runBlocking {
            container.expenseRepository.save(Expense(0, LocalDate.of(2025, 2, 14), 47_15, "CHF", "FLOWER SHOP $tag", null, false, null, null))
        }
        openTab("Spending")
        rule.onNodeWithContentDescription("Search transactions").performClick()
        waitForText("Every month is searched", substring = true)

        rule.onNode(hasSetTextAction()).performTextInput(tag.lowercase())
        waitForText("FLOWER SHOP $tag")
        waitForText("1 transaction")

        // By amount instead.
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("47.15")
        waitForText("FLOWER SHOP $tag")

        rule.onNodeWithText("FLOWER SHOP $tag").performClick()
        waitForText("Edit expense")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        // Back on the search, still showing what was found.
        waitForText("FLOWER SHOP $tag")
    }

    @Test
    fun theScrollThumbJumpsToTheOldestResultAndShowsItsMonth() {
        val tag = "FASTSCROLL${System.nanoTime() % 100000}"
        // Weekly from a fixed day, so the test doesn't depend on today.
        val newest = LocalDate.of(2026, 6, 1)
        val oldest = newest.minusWeeks(79)
        runBlocking {
            (0L..79L).forEach { week ->
                container.expenseRepository.save(Expense(0, newest.minusWeeks(week), 10_00 + week, "CHF", "$tag ROW$week", null, false, null, null))
            }
        }
        openTab("Spending")
        rule.onNodeWithContentDescription("Search transactions").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(tag.lowercase())
        waitForText("$tag ROW0")
        Espresso.closeSoftKeyboard()

        // The thumb shows once the list moves.
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("fastScroller")).fetchSemanticsNodes().isNotEmpty() }
        val month = YearMonth.from(oldest).format(localDateFormat("MMMyyyy"))
        rule.onNode(hasTestTag("fastScroller")).performTouchInput {
            down(center)
            moveBy(Offset(0f, 5_000f)) // past the bottom: the thumb stops at the end
        }
        waitForText(month) // the bubble beside the thumb
        rule.onNode(hasTestTag("fastScroller")).performTouchInput { up() }
        waitForText("$tag ROW79")
    }
}
