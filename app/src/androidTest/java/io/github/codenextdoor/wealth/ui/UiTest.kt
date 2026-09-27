package io.github.codenextdoor.wealth.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.domain.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Base for UI tests: the real app (in-memory database, see WealthTestRunner),
 * Espresso-Intents for system pickers, and small helpers.
 */
abstract class UiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    protected val container get() = (rule.activity.application as WealthApplication).container
    protected val context get() = rule.activity

    @Before
    fun startUi() {
        Intents.init()
        container.appLock.disable()
    }

    @After
    fun stopUi() {
        Intents.release()
        container.appLock.disable()
    }

    /** Switches to a bottom tab (the tab, not a same-named page title). */
    protected fun openTab(label: String) =
        rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()

    /** Waits (real time) until [text] is on screen. */
    protected fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) {
        try {
            rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            // Log what was on screen instead, to make failures diagnosable (logcat tag "UiTest").
            rule.onAllNodes(isRoot(), useUnmergedTree = true).printToLog("UiTest")
            throw AssertionError("\"$text\" not shown within $timeoutMs ms (screen logged under tag UiTest)", e)
        }
    }

    protected fun isShown(text: String, substring: Boolean = false) =
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    /**
     * The editable field labelled [label]: exactly by default (so "Password"
     * doesn't also match "Repeat password"), or by the start of a long label.
     */
    protected fun field(label: String, substring: Boolean = false): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(label, substring = substring))

    protected fun typeInto(label: String, text: String, substring: Boolean = false) =
        field(label, substring).performTextInput(text)

    /** Answers the app's "open a file" request with [file], without showing the system picker. */
    protected fun stubOpenDocument(file: File) {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
    }

    /** Answers the app's "create a file" request with [file], without showing the system picker. */
    protected fun stubCreateDocument(file: File) {
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
    }

    /** A file in the app's cache (removed after each test by the caller). */
    protected fun cacheFile(name: String) = File(context.cacheDir, name).also { it.delete() }

    /** Adds a CHF bank account directly through the repository; returns its id. */
    protected fun addBankAccount(name: String, balanceMinor: Long = 1_000_00): Long = runBlocking {
        val type = container.catalogRepository.accountTypes.first().first { it.name == "Bank account" }
        container.accountRepository.save(
            Account(0, name, type.id, "CHF", type.countryId, balanceMinor, Instant.EPOCH, null, null),
            balanceDate = LocalDate.now(),
            recordBalance = true,
        )
        container.accountRepository.accounts.first().first { it.name == name }.id
    }
}
