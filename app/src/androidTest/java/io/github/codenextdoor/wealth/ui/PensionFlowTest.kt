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
    fun onlyTypesThatGrowOfferIt() {
        openTab("Accounts")
        rule.onNodeWithContentDescription("Add asset account").tap()
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Bank account · ", substring = true).performScrollTo().tap()
        rule.waitForIdle()
        assertTrue(!isShown("Grow between statements"))
    }
}
