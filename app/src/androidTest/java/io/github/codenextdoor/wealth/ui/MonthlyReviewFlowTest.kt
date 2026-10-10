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
import io.github.codenextdoor.wealth.domain.Account
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first

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

    @Test
    fun theReviewSaysHowMuchCameFromExchangeRates() {
        val lastMonth = YearMonth.from(LocalDate.now()).minusMonths(1)
        val name = "Review NRE ${System.nanoTime() % 100000}"
        runBlocking {
            val type = container.catalogRepository.accountTypes.first().first { it.name == "Bank account" }
            container.accountRepository.save(
                Account(0, name, type.id, "INR", type.countryId, 1_000_000_00, Instant.EPOCH, null, null),
                balanceDate = lastMonth.minusMonths(1).atEndOfMonth(),
                recordBalance = true,
            )
            // Typed rates win over downloaded ones: the rupee weakens during the month.
            container.currencyRepository.setRate("CHF", "INR", BigDecimal("100"), lastMonth.minusMonths(1).atEndOfMonth())
            container.currencyRepository.setRate("CHF", "INR", BigDecimal("110"), lastMonth.atEndOfMonth())
        }
        openTab("Overview")
        scrollingList().performScrollToNode(hasText("Monthly reviews"))
        rule.onNodeWithText("Monthly reviews").tap()
        waitForText(title(lastMonth))
        waitForText("From exchange rates")
        waitForText("INR −", substring = true)
    }
}
