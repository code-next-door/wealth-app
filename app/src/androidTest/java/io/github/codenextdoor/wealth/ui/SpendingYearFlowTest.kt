package io.github.codenextdoor.wealth.ui

import android.text.format.DateFormat
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The year view on the Spending tab: tap a month, browse to last year and pick one there. */
@RunWith(AndroidJUnit4::class)
class SpendingYearFlowTest : UiTest() {

    private val title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy"))

    private fun spend(date: LocalDate) = runBlocking {
        container.expenseRepository.save(Expense(0, date, 12_00, "CHF", "Year view ${date.year}", null, false, null, null))
    }

    @Test
    fun tapAMonthThenPickOneFromLastYear() {
        val thisMonth = YearMonth.now()
        val earlier = if (thisMonth.monthValue > 1) thisMonth.minusMonths(1) else thisMonth
        val lastJanuary = YearMonth.of(thisMonth.year - 1, 1)
        spend(earlier.atDay(1))
        spend(lastJanuary.atDay(5)) // lets the year view go back a year

        openTab("Spending")
        // "<Month year>, <amount>": the tile for a month of this year.
        rule.onNodeWithContentDescription(earlier.format(title), substring = true).performClick()
        waitForText(earlier.format(title))

        rule.onNodeWithContentDescription("Previous year").performClick()
        waitForText((thisMonth.year - 1).toString(), substring = true)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(lastJanuary.format(title), substring = true))
        rule.onNodeWithContentDescription(lastJanuary.format(title), substring = true).performClick()
        waitForText(lastJanuary.format(title))
    }
}
