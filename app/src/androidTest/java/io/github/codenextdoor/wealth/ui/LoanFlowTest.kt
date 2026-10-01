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

    @Test
    fun anEmiShowsItsInterestAndThePrincipalIsNotCounted() {
        val tag = System.nanoTime() % 100000
        val emi = "EMI $tag"
        runBlocking {
            val catalog = container.catalogRepository
            catalog.addExpenseCategory("Home loan $tag")
            val category = catalog.expenseCategories.first().single { it.name == "Home loan $tag" }.id
            val type = catalog.accountTypes.first().single { it.seedKey == "mortgage" }
            val firstEmi = java.time.LocalDate.now().minusMonths(6)
            container.accountRepository.save(
                io.github.codenextdoor.wealth.domain.Account(0, "Mortgage $tag", type.id, "CHF", null, 400_000_00, java.time.Instant.EPOCH, null, null),
                balanceDate = firstEmi.minusDays(1),
                recordBalance = true,
            )
            val account = container.accountRepository.accounts.first().single { it.name == "Mortgage $tag" }.id
            container.loanRepository.save(
                io.github.codenextdoor.wealth.domain.Loan(account, 400_000_00, firstEmi, 3_000_00, java.math.BigDecimal("2"), emiCategoryId = category),
            )
            container.expenseRepository.save(
                io.github.codenextdoor.wealth.domain.Expense(0, java.time.LocalDate.now(), 3_000_00, "CHF", emi, category, true, null, null),
            )
        }
        openTab("Spending")
        val list = rule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText(emi))
        waitForText("interest counted", substring = true) // the EMI row: only its interest counts
        list.performScrollToNode(hasText("Not counted as spending") and androidx.compose.ui.test.hasClickAction())
        rule.onNode(hasText("Not counted as spending") and androidx.compose.ui.test.hasClickAction()).tap()
        list.performScrollToNode(hasText("Principal repaid", substring = true))
        waitForText("Principal repaid", substring = true)
    }
}
