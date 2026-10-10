package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Overview › Monthly reviews: last month's figures and the month arrows. */
@RunWith(AndroidJUnit4::class)
class MonthlyReviewFlowTest : UiTest() {

    private fun title(month: YearMonth) = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"

    @Test
    fun lastMonthsReviewShowsWhatMovedAndTheArrowsChangeMonth() {
        val lastMonth = YearMonth.from(LocalDate.now()).minusMonths(1)
        val name = "Review savings ${System.nanoTime() % 100000}"
        val id = addBankAccount(name, balanceMinor = 1_000_00)
        runBlocking {
            container.accountRepository.addHistoryEntry(id, lastMonth.minusMonths(1).atEndOfMonth(), 1_000_00)
            container.accountRepository.addHistoryEntry(id, lastMonth.atEndOfMonth(), 1_234_00)
        }
        openTab("Overview")
        scrollingList().performScrollToNode(hasText("Monthly reviews"))
        rule.onNodeWithText("Monthly reviews").tap()

        waitForText(title(lastMonth))
        waitForText("Moved it most")
        scrollingList().performScrollToNode(hasText(name))
        waitForText(name)

        rule.onNodeWithContentDescription("Previous month").performClick()
        waitForText(title(lastMonth.minusMonths(1)))
        rule.onNodeWithContentDescription("Next month").performClick()
        waitForText(title(lastMonth))
    }
}
