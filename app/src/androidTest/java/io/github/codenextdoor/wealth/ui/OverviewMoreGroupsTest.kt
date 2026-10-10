package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Overview › Where your money is: the folded "N more" opens to show each small group. */
@RunWith(AndroidJUnit4::class)
class OverviewMoreGroupsTest : UiTest() {

    @Test
    fun theFoldedGroupsOpen() {
        val tag = System.nanoTime() % 100000
        val small = "Tiny type $tag"
        runBlocking {
            val catalog = container.catalogRepository
            // Six big types and a tiny one: the tiny one is folded, whatever else the test data holds.
            (1..6).forEach { catalog.addAccountType("Big type $tag $it", AssetKind.ASSET, null) }
            catalog.addAccountType(small, AssetKind.ASSET, null)
            val types = catalog.accountTypes.first().associate { it.name to it.id }
            suspend fun add(name: String, type: String, minor: Long) = container.accountRepository.save(
                Account(0, name, types.getValue(type), "CHF", null, minor, Instant.EPOCH, null, null),
                balanceDate = LocalDate.of(2026, 1, 1),
                recordBalance = true,
            )
            (1..6).forEach { add("Big $tag $it", "Big type $tag $it", 1_000_000_000_00L + it) }
            add("Tiny $tag", small, 1_00)
        }

        openTab("Overview")
        val list = rule.onNode(hasScrollToNodeAction())
        val more = hasText(" more", substring = true) and hasText("%", substring = true) and hasClickAction()
        list.performScrollToNode(more)
        rule.onNode(more).tap()
        list.performScrollToNode(hasText(small))
        waitForText(small)
    }
}
