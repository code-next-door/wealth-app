package io.github.codenextdoor.wealth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.WealthApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A statement opened or shared from another app (Gmail, Files) opens Wealth on the
 * right screen: one file in Import, several in Build history; Back goes into the app.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<WealthApplication>()
    private val container get() = context.container
    private val files = mutableListOf<File>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        container.appLock.disable()
        container.onboarding.skipAll()
        container.tour.stop()
        container.reminderPreferences.setEnabled(false)
    }

    @After
    fun tearDown() {
        scenario?.close()
        files.forEach { it.delete() }
    }

    private fun statement(name: String, asset: String = "fake-ubs-statement.pdf"): Uri {
        val file = File(context.cacheDir, name).also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset)
            .use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    private fun launch(intent: Intent) {
        scenario = ActivityScenario.launch(intent.setClass(context, MainActivity::class.java))
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun openingAStatementGoesStraightToItsImport() {
        launch(Intent(Intent.ACTION_VIEW).setDataAndType(statement("open-with-statement.pdf"), "application/pdf"))
        waitForText("UBS account statement", substring = true)
        waitForText("Import 3 transactions")
        Espresso.pressBack()
        waitForText("Overview") // the home screen, inside the app
    }

    @Test
    fun aLockedStatementAsksForItsPasswordAndThenImports() {
        launch(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, statement("locked.pdf", "locked-ubs-statement.pdf")))
        waitForText("This statement is locked with a password")
        val field = hasSetTextAction()
        rule.onNode(field).performTextInput("wrong")
        rule.onNode(hasText("Open") and hasClickAction()).performClick()
        waitForText("That password didn't open it", substring = true)
        rule.onNode(field).performTextInput("test-pass")
        rule.onNode(hasText("Open") and hasClickAction()).performClick()
        waitForText("Import 3 transactions")
    }

    @Test
    fun sharingSeveralStatementsGoesToBuildHistory() {
        launch(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/pdf")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(statement("shared-1.pdf"), statement("shared-2.pdf"))),
        )
        waitForText("Build history from statements")
        waitForText("shared-1.pdf", substring = true)
        waitForText("shared-2.pdf", substring = true)
    }
}
