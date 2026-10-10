package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.ui.localDateFormat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Search reached from a category (Spending) and from a shop (a transaction), with its month chart. */
@RunWith(AndroidJUnit4::class)
class SearchFiltersFlowTest : ScreenJourneys() {

    @Test
    fun aCategoryOpensSearchWithItsChartAndABarPicksAMonth() {
        val category = addCategorySpend()
        searchCategoryFromSpending(category)
        waitForText("Money out, last 12 months")
        waitForText(category) // the category chip shows it
        waitForText("$category spend")

        val month = YearMonth.now().format(localDateFormat("MMMMyyyy"))
        // Screen readers pick a month through the chart's actions; a finger taps its bar.
        val chart = rule.onNode(hasContentDescription("Money out, last 12 months", substring = true))
        val action = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label.startsWith(month) }
        rule.runOnUiThread { action.action() }
        waitForText("Only $month")
        waitForText("$category spend")
        rule.onNodeWithText("Only $month").performClick() // removes the month again
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Only $month")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aTransactionsShopOpensSearchForItsOtherTransactions() {
        val line = addShopSpend()
        val shop = line.substringBefore(" ")
        searchShopFromTransaction(line)
        waitForText("Money out, last 12 months")
        waitForText(shop) // in the search box
        waitForText(line)
        waitForText("Money out, last 12 months")
    }
}
