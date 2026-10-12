package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A pillar 2 pension that grows between certificates, shown on the Accounts tab. */
@RunWith(AndroidJUnit4::class)
class PensionFlowTest : UiTest() {

    @Test
    fun addAGrowingPensionAndSeeItsValue() {
        val name = "Pension ${System.nanoTime() % 100000}"
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add asset account").tap()
        typeInto("Account name", name, substring = true)
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Pillar 2 pension · ", substring = true).performScrollTo().tap()
        typeInto("Balance", "100000")

        rule.onNode(hasText("Grow between statements") and isToggleable()).performScrollTo().tap()
        typeInto("Contributions a year", "24000")
        typeInto("Interest credited (% a year)", "1.25")
        waitForText("(calculated)", substring = true) // the preview, once the terms are there
        rule.onNodeWithText("Save").performScrollTo().tap()

        // Saved with its terms; the Accounts tab marks it as calculated.
        rule.waitUntil(10_000) { runBlocking { container.accountRepository.accounts.first().any { it.name == name } } }
        val id = runBlocking { container.accountRepository.accounts.first().single { it.name == name }.id }
        val pension = runBlocking { container.pensionRepository.forAccount(id) }!!
        assertEquals(24_000_00L, pension.yearlyContributionMinor)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        waitForText("calculated", substring = true)
        assertTrue(isShown(name))
    }

    @Test
    fun calculatedMonthEndsShowInTheHistory() {
        val name = "Month-end pension ${System.nanoTime() % 100000}"
        val id = runBlocking {
            val type = container.catalogRepository.accountTypes.first().single { it.seedKey == "ch_pillar2" }
            container.accountRepository.save(
                io.github.codenextdoor.wealth.domain.Account(0, name, type.id, "CHF", null, 100_000_00, java.time.Instant.EPOCH, null, null),
                balanceDate = java.time.LocalDate.now().minusMonths(6),
                recordBalance = true,
            )
            val account = container.accountRepository.accounts.first().single { it.name == name }.id
            container.pensionRepository.save(io.github.codenextdoor.wealth.domain.Pension(account, 24_000_00, java.math.BigDecimal("1.25")))
            account
        }
        openTab("Accounts")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onNodeWithText(name).tap()

        // Month-ends since the certificate are listed as calculated; only the certificate is saved.
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Save your own figure")).fetchSemanticsNodes().size >= 5 }
        assertEquals(1, runBlocking { container.accountRepository.observeHistory(id).first().size })
    }

    @Test
    fun onlyTypesThatGrowOfferIt() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add asset account").tap()
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Bank account · ", substring = true).performScrollTo().tap()
        rule.waitForIdle()
        assertTrue(!isShown("Grow between statements"))
    }
}
