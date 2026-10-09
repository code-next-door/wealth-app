package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasSetTextAction
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
}
