package io.github.codenextdoor.wealth.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.junit.After
import org.junit.Before
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import io.github.codenextdoor.wealth.domain.Expense
import java.time.LocalDate

/**
 * Every screen of the app and how to reach it by tapping from Home, shared by the
 * navigation map and the screen checks. Screens are told apart by their title (a
 * heading, see ScreenTitle). New screen or new way in → new journey.
 */
abstract class ScreenJourneys : UiTest() {

    /** A made-up one-row bank export for the Import journey. */
    private val statement by lazy {
        cacheFile("journeys.csv").apply { writeText("Booking date;Description1;Debit;Credit;Balance\n2026-01-15;\"JOURNEY TEST\";-1.00;;10.00\n") }
    }

    @After
    fun deleteStatement() {
        cacheFile("journeys.csv")
    }

    /** Import and Build history open the file picker straight away: answer as if it was closed. */
    @Before
    fun closeFilePickers() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    /** [route]: the Routes class it opens; [backTo]: the title Back lands on. */
    protected class Journey(val route: String, val title: String, val backTo: String, val open: () -> Unit)

    /** Routes no tap leads to any more; kept only so an older saved screen still opens. */
    protected val legacyRoutes = setOf("Rules", "Recurring", "RecurringEdit")

    private fun fromSettings(row: String) {
        rule.onNodeWithContentDescription("Settings").performClick()
        awaitTitle("Settings")
        rule.onNodeWithText(row).performScrollTo().tap()
    }

    private fun scrollToAndTap(description: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).tap()
    }

    /** A new category with this month's biggest spend (so it's a slice of its own); returns its name. */
    protected fun addCategorySpend(): String = runBlocking {
        val name = "Journey category ${System.nanoTime() % 100000}"
        val catalog = container.catalogRepository
        catalog.addExpenseCategory(name)
        val id = catalog.expenseCategories.first().single { it.name == name }.id
        container.expenseRepository.save(Expense(0, LocalDate.now(), 9_000_000_00L, "CHF", "$name spend", id, true, null, null))
        name
    }

    /** A transaction from a made-up shop this month; returns its statement line. */
    protected fun addShopSpend(): String = runBlocking {
        val line = "JRNYSHOP${System.nanoTime() % 100000} 4521 ZURICH"
        container.expenseRepository.save(Expense(0, LocalDate.now(), 12_00, "CHF", line, null, false, null, null))
        line
    }

    /** A made-up monthly charge, three months running up to today; returns its shop word. */
    protected fun addRegularPayment(): String = runBlocking {
        val shop = "RGLR${System.nanoTime() % 100000}"
        (0L..2L).forEach { months ->
            container.expenseRepository.save(Expense(0, LocalDate.now().minusMonths(months), 7_77, "CHF", "$shop 4521 ZURICH", null, false, null, null))
        }
        shop
    }

    /** Spending › the Regular payments card. */
    protected fun openRegularPayments() {
        openTab("Spending")
        val card = hasText("Regular payments") and hasClickAction()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(card)
        rule.onNode(card).tap()
    }

    /** Spending › tap the category's slice (its legend line) › "… in every month". */
    protected fun searchCategoryFromSpending(category: String) {
        openTab("Spending")
        val list = rule.onNode(hasScrollToNodeAction())
        val legendLine = hasText(category) and hasText("%", substring = true) and hasClickAction()
        // Clicked through their click action: a touch here makes the automatic accessibility check
        // measure the row half scrolled under the top of the screen (at large text) as tiny.
        list.performScrollToNode(legendLine)
        rule.onNode(legendLine).performSemanticsAction(SemanticsActions.OnClick)
        // Same card as the legend line, so already there: no second scroll (a scroll runs the
        // automatic check while rows above the card are half under the top).
        val link = hasText("$category in every month", substring = true) and hasClickAction()
        rule.waitUntil(5_000) { rule.onAllNodes(link).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(link).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Spending › Search › the transaction › "More from …" (Search: a Spending filter may hide its row). */
    protected fun searchShopFromTransaction(line: String) {
        // The category journey may have left Spending scrolled: click actions, as there.
        rule.onNode(hasText("Spending") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithContentDescription("Search transactions").performSemanticsAction(SemanticsActions.OnClick)
        typeInto("Search transactions", line.substringBefore(" "))
        rule.onNode(isFocused() and hasSetTextAction()).performImeAction() // closes the keyboard
        waitForText(line)
        rule.onNodeWithText(line).tap()
        waitForText("More from", substring = true)
        rule.onNodeWithText("More from", substring = true).tap()
    }

    protected val journeys = listOf(
        Journey("Settings", "Settings", backTo = "Wealth") {
            openTab("Overview"); rule.onNodeWithContentDescription("Settings").performClick()
        },
        Journey("Currencies", "Currencies & exchange rates", backTo = "Settings") { fromSettings("Currencies & exchange rates") },
        Journey("AccountTypes", "Account types", backTo = "Settings") { fromSettings("Account types") },
        Journey("Categories", "Categories and patterns", backTo = "Settings") { fromSettings("Categories and patterns") },
        Journey("CategoryDetail", "Groceries", backTo = "Categories and patterns") {
            fromSettings("Categories and patterns")
            awaitTitle("Categories and patterns")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Groceries"))
            rule.onNodeWithText("Groceries").tap()
        },
        Journey("Countries", "Countries", backTo = "Settings") { fromSettings("Countries") },
        Journey("Backfill", "Build history from statements", backTo = "Settings") { fromSettings("Build history from statements") },
        // The Overview's link is there once an account exists.
        Journey("MonthlyReview", "Monthly review", backTo = "Wealth") {
            if (runBlocking { container.accountRepository.accounts.first() }.isEmpty()) addBankAccount("Journey account")
            openTab("Overview")
            scrollingList().performScrollToNode(hasText("Monthly reviews"))
            rule.onNodeWithText("Monthly reviews").tap()
        },
        Journey("History", "Balance history", backTo = "Accounts") {
            openTab("Accounts"); rule.onNodeWithContentDescription("Balance history").performClick()
        },
        Journey("Coverage", "Statement coverage", backTo = "Accounts") {
            openTab("Accounts"); rule.onNodeWithContentDescription("Statement coverage").performClick()
        },
        Journey("AccountEdit", "Add account", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add asset account") },
        Journey("AccountEdit", "Add account", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add liability") },
        Journey("GrantEdit", "Add stock grant", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add stock grant") },
        Journey("HouseEdit", "Add house", backTo = "House") {
            openTab("House"); rule.onNodeWithContentDescription("Add house").performClick()
        },
        Journey("Search", "Search", backTo = "Spending") {
            openTab("Spending"); rule.onNodeWithContentDescription("Search transactions").performClick()
        },
        Journey("ExpenseEdit", "Add expense", backTo = "Spending") {
            openTab("Spending"); rule.onNodeWithContentDescription("Add expense").performClick()
        },
        // Import opens the picker first, and closes again if nothing is picked: give it a file.
        Journey("Import", "Import statement", backTo = "Spending") {
            stubOpenDocument(statement)
            openTab("Spending"); rule.onNodeWithContentDescription("Import statement").performClick()
        },
        Journey("RegularPayments", "Regular payments", backTo = "Spending") { addRegularPayment(); openRegularPayments() },
        // Last: the category one leaves the Spending list scrolled down, and a touch on a scrolled
        // list makes the automatic accessibility check measure rows half under the top as tiny.
        Journey("SearchFor", "Search", backTo = "Edit expense") { searchShopFromTransaction(addShopSpend()) },
        Journey("SearchFor", "Search", backTo = "Spending") { searchCategoryFromSpending(addCategorySpend()) },
    )

    /**
     * System Back, once. With the keyboard open, Back would first close it (and, while it
     * animates, left a screen change half done in tests), so a person's way out first:
     * the keyboard's own action key (Search closes it), then Back.
     */
    protected fun back() {
        val typing = isFocused() and hasSetTextAction()
        if (rule.onAllNodes(typing).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNode(typing).performImeAction()
            rule.waitUntil(5_000) { rule.onAllNodes(typing).fetchSemanticsNodes().isEmpty() }
            rule.waitForIdle()
        }
        Espresso.pressBack()
    }

    /**
     * The titles on screen (headings); one once a screen change has finished. None
     * while the activity is being recreated (there's briefly no Compose content).
     */
    private fun titles(): List<String> = try {
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } }
    } catch (_: IllegalStateException) {
        emptyList()
    }

    protected fun title(): String? {
        try {
            rule.waitUntil(10_000) { titles().size == 1 }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("expected one screen title, found ${titles()}", e)
        }
        return titles().single()
    }

    protected fun awaitTitle(expected: String, step: String = expected) = awaitTitle(setOf(expected), step)

    protected fun awaitTitle(expected: Set<String>, step: String = expected.joinToString()) {
        try {
            rule.waitUntil(10_000) { titles().let { it.size == 1 && it.single() in expected } }
        } catch (e: ComposeTimeoutException) {
            logScreen()
            throw AssertionError("$step: expected title $expected, found ${titles()} (screen logged under tag UiTest)", e)
        }
    }

    protected companion object {
        /** Home's title: the app name on Overview, else the tab's name. */
        val HOME_TITLES = setOf("Wealth", "Accounts", "House", "Spending")
    }

    /** Back until Home (two steps from Settings' sub-screens). */
    protected fun backToHome() {
        while (title() !in HOME_TITLES) back()
    }
}
