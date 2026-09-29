package io.github.codenextdoor.wealth.ui

import android.text.format.DateFormat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Spending tab changes month by swiping (no month arrows: the tiles above do the rest). */
@RunWith(AndroidJUnit4::class)
class SpendingSwipeFlowTest : UiTest() {

    private val title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy"))
    private val thisMonth = YearMonth.now()
    private val lastMonth = thisMonth.minusMonths(1)

    /** The month header's title (the tiles only have descriptions, not text). */
    private fun showing(month: YearMonth) = isShown(month.format(title))

    private fun list() = rule.onNode(hasScrollToNodeAction())

    @Test
    fun swipeRightForTheMonthBeforeAndLeftBackButNotPastThisMonth() {
        openTab("Spending")
        waitForText(thisMonth.format(title))

        list().performTouchInput { swipeRight() }
        waitForText(lastMonth.format(title))

        list().performTouchInput { swipeLeft() }
        waitForText(thisMonth.format(title))

        list().performTouchInput { swipeLeft() } // nothing after this month
        rule.waitForIdle()
        check(showing(thisMonth) && !showing(thisMonth.plusMonths(1)))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun screenReadersGetPreviousAndNextMonthActions() {
        openTab("Spending")
        waitForText(thisMonth.format(title))
        val header = rule.onNode(hasText(thisMonth.format(title)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        header.performCustomAccessibilityActionWithLabel("Previous month")
        waitForText(lastMonth.format(title))
        rule.onNode(hasText(lastMonth.format(title)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .performCustomAccessibilityActionWithLabel("Next month")
        waitForText(thisMonth.format(title))
    }
}
