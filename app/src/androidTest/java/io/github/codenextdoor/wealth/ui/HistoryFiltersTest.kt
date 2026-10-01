package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import io.github.codenextdoor.wealth.domain.Account
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The balance history's account filters wrap onto more lines: none is hidden off to the side. */
@RunWith(AndroidJUnit4::class)
class HistoryFiltersTest : UiTest() {

    @Test
    fun everyAccountFilterIsLaidOutWithoutSidewaysScrolling() {
        val tag = System.nanoTime() % 100000
        val names = (1..12).map { "Filter account $tag $it" }
        val ids = names.map { addBankAccount(it) }
        // History on two days, so the Overview shows its history card with the link.
        runBlocking { container.accountRepository.addHistoryEntry(ids.first(), LocalDate.now().minusMonths(1), 500_00) }

        waitForText("Net worth over time")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("See and edit all balance entries"))
        rule.onNodeWithText("See and edit all balance entries").tap()
        waitForText("All accounts")

        // A sideways list only creates the chips that fit; a wrapping one has them all.
        names.forEach { name -> rule.onNode(hasText(name) and isSelectable()).performScrollTo() }
        // And nothing scrolls sideways.
        val sideways = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes()
        check(sideways.isEmpty()) { "a horizontally scrolling list is still there" }
    }

    @Test
    fun aLongValueWrapsInTheRightHalfAndLeavesTheNameItsHalf() {
        val name = "Google Stocks ${System.nanoTime() % 100000}"
        runBlocking {
            val type = container.catalogRepository.accountTypes.first().first { it.holdsShares }
            container.accountRepository.save(
                Account(0, name, type.id, "USD", null, 767_62, Instant.EPOCH, null, null, shareSymbol = "GOOG", units = BigDecimal("1455.9")),
                balanceDate = LocalDate.now().minusMonths(1),
                recordBalance = true,
            )
            val id = container.accountRepository.accounts.first().single { it.name == name }.id
            container.accountRepository.addHistoryEntry(id, LocalDate.now(), 767_62, units = BigDecimal("1455.9"))
        }
        waitForText("Net worth over time")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("See and edit all balance entries"))
        rule.onNodeWithText("See and edit all balance entries").tap()
        waitForText("All accounts")
        rule.onNode(hasText(name) and isSelectable()).performScrollTo().tap() // just this account
        waitForText("GOOG +", substring = true)

        val value = rule.onAllNodes(hasText("GOOG +", substring = true), useUnmergedTree = true).fetchSemanticsNodes().first()
        val label = rule.onAllNodes(hasText(name), useUnmergedTree = true).fetchSemanticsNodes()
            .first { node -> generateSequence(node.parent, SemanticsNode::parent).none { SemanticsProperties.Selected in it.config } } // not the chip
        val row = generateSequence(value.parent, SemanticsNode::parent).first { SemanticsActions.OnClick in it.config }
        val middle = row.positionInRoot.x + row.size.width / 2f
        check(label.positionInRoot.x + label.size.width <= value.positionInRoot.x) { "the name runs into the value" }
        check(value.positionInRoot.x >= row.positionInRoot.x + row.size.width * 0.4f) { "the value takes more than its half" }
        check(label.positionInRoot.x < middle) { "the name isn't in the left half" }
    }
}
