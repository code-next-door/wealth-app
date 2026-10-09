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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4

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
        Journey("History", "Balance history", backTo = "Accounts") {
            openTab("Accounts"); rule.onNodeWithContentDescription("Balance history").performClick()
        },
        Journey("AccountEdit", "Add account", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add asset account") },
        Journey("AccountEdit", "Add account", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add liability") },
        Journey("GrantEdit", "Add stock grant", backTo = "Accounts") { openTab("Accounts"); scrollToAndTap("Add stock grant") },
        Journey("HouseEdit", "Add house", backTo = "House") {
            openTab("House"); rule.onNodeWithContentDescription("Add house").performClick()
        },
        Journey("ExpenseEdit", "Add expense", backTo = "Spending") {
            openTab("Spending"); rule.onNodeWithContentDescription("Add expense").performClick()
        },
        // Import opens the picker first, and closes again if nothing is picked: give it a file.
        Journey("Import", "Import statement", backTo = "Spending") {
            stubOpenDocument(statement)
            openTab("Spending"); rule.onNodeWithContentDescription("Import statement").performClick()
        },
    )

    protected fun back() {
        Espresso.closeSoftKeyboard()
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
        rule.waitUntil(10_000) { titles().size == 1 }
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
