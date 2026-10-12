package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A saved estimate (e.g. a longer statement's month-end) is marked calculated in the account's history. */
@RunWith(AndroidJUnit4::class)
class EstimatedBalanceTest : UiTest() {

    @Test
    fun aSavedEstimateShowsAsCalculated() {
        val name = "Estimated funds ${System.nanoTime() % 100000}"
        val id = addBankAccount(name)
        val day = LocalDate.of(2025, 3, 31)
        runBlocking { container.accountRepository.addHistoryEntry(id, day, 400_00, estimated = true) }

        openTab("Accounts")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onNodeWithText(name).tap()
        val label = "${day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} · calculated"
        // The form is one scrolling column (not a lazy list): the row exists, scroll it into view.
        waitForText(label)
        rule.onNodeWithText(label).performScrollTo()
    }
}
