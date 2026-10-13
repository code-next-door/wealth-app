package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Accounts › Statement coverage (top bar) shows a missing month, and adding its balance fills it. */
@RunWith(AndroidJUnit4::class)
class CoverageFlowTest : UiTest() {

    @Test
    fun aMissingMonthCanBeFilledFromTheCoverageScreen() {
        val name = "Coverage savings ${System.nanoTime() % 100000}"
        // Balances at the end of October 2024 and a closing zero in December: November is missing.
        val id = runBlocking {
            val type = container.catalogRepository.accountTypes.first().first { it.name == "Bank account" }
            val id = container.accountRepository.save(
                Account(0, name, type.id, "CHF", type.countryId, 100_00, Instant.EPOCH, null, null),
                balanceDate = LocalDate.of(2024, 10, 31),
                recordBalance = true,
            )
            container.accountRepository.addHistoryEntry(id, LocalDate.of(2024, 12, 31), 0)
            id
        }

        openTab("Accounts")
        rule.onNodeWithContentDescription("Statement coverage").performClick()
        waitForText("Saved")
        repeat(YearMonth.now().minusMonths(1).year - 2024) { rule.onNodeWithContentDescription("Previous year").tap() }
        waitForText("2024")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        // The card is one tappable node: its name and what's missing are read out together.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(name) and hasText("Missing: Nov", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(name).tap()
        waitForText("Add balance for November 2024")
        rule.onNodeWithText("Add balance for November 2024").tap()
        waitForText("Add past balance")
        typeInto("Balance", "80")
        rule.onNode(hasText("Save") and hasAnyAncestor(isDialog())).tap()

        rule.waitUntil(10_000) {
            runBlocking { container.accountRepository.observeHistory(id).first() }.any { it.date == LocalDate.of(2024, 11, 30) && it.balanceMinor == 80_00L }
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(name) and hasText("Nothing missing")).fetchSemanticsNodes().isNotEmpty() }
    }
}
