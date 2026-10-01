package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasContentDescription
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

/** A mortgage whose outstanding the app calculates from its terms, shown on the Accounts tab. */
@RunWith(AndroidJUnit4::class)
class LoanFlowTest : UiTest() {

    @Test
    fun addACalculatedMortgageAndSeeItsOutstanding() {
        val name = "Mortgage ${System.nanoTime() % 100000}"
        openTab("Accounts")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Add liability"))
        rule.onNodeWithContentDescription("Add liability").tap()
        typeInto("Account name", name, substring = true)
        rule.onNodeWithText("Type").performClick()
        rule.onNodeWithText("Mortgage · ", substring = true).performScrollTo().tap()

        rule.onNode(hasText("Calculate the balance") and isToggleable()).performScrollTo().tap()
        typeInto("Loan amount", "2000000")
        typeInto("EMI (monthly payment)", "17356")
        typeInto("Interest rate (% a year)", "8.5")
        waitForText("Outstanding today", substring = true) // the preview, once the terms are there
        rule.onNodeWithText("Save").performScrollTo().tap()

        // Saved with its terms; the Accounts tab marks it as calculated.
        rule.waitUntil(10_000) { runBlocking { container.accountRepository.accounts.first().any { it.name == name } } }
        val id = runBlocking { container.accountRepository.accounts.first().single { it.name == name }.id }
        val loan = runBlocking { container.loanRepository.forAccount(id) }!!
        assertEquals(2_000_000_00L, loan.principalMinor)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        waitForText("calculated", substring = true)
        assertTrue(isShown(name))
    }
}
