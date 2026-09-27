package io.github.codenextdoor.wealth.ui

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Typing: fast bursts of keys arrive intact, and password fields are secure. */
@RunWith(AndroidJUnit4::class)
class TextInputTest : UiTest() {

    private fun keyFor(c: Char): Key = when (c) {
        in 'a'..'z' -> Key(KeyEvent.KEYCODE_A + (c - 'a'))
        in '0'..'9' -> Key(KeyEvent.KEYCODE_0 + (c - '0'))
        ' ' -> Key(KeyEvent.KEYCODE_SPACE)
        else -> error("no key for '$c'")
    }

    private fun editableText(label: String): String =
        field(label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fastTypingArrivesIntactAndRulesApply() {
        val typed = "twint migros 4521 zuerich hb bahnhof"
        openTab("Spending")
        rule.onNodeWithContentDescription("Add expense").performClick()
        field("Description, e.g. Migros Zürich").performClick()

        // One burst of key presses, with no waiting in between (a fast typist).
        field("Description, e.g. Migros Zürich").performKeyInput {
            typed.forEach { pressKey(keyFor(it), pressDurationMillis = 1) }
        }
        rule.waitForIdle()

        assertEquals(typed, editableText("Description, e.g. Migros Zürich"))
        waitForText("Picked by the rule “MIGROS”")
    }

    @Test
    fun backupPasswordFieldsAreSecure() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Export encrypted backup").performClick()
        waitForText("Choose a backup password")

        val passwordFields = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).fetchSemanticsNodes()
        assertEquals("both fields are password fields", 2, passwordFields.size)

        typeInto("Password", "sup3r-secret")
        rule.waitForIdle()
        val node = field("Password").fetchSemanticsNode()
        // Marked as a password, so screen readers say "dot" instead of the characters.
        assertTrue(node.config.contains(SemanticsProperties.Password))
        // No copy/cut, so the password can't be lifted to the clipboard.
        assertFalse(node.config.contains(SemanticsActions.CopyText))
        assertFalse(node.config.contains(SemanticsActions.CutText))
        // Known limitation of Compose's secure field (not ours): the typed text is
        // still present in the accessibility tree, unlike classic Android password
        // fields. On screen it's masked; accessibility services see the Password flag.
    }

    @Test
    fun backupNeedsMatchingPasswordsOfEightCharacters() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Export encrypted backup").performClick()
        waitForText("Choose a backup password")

        typeInto("Password", "short")
        typeInto("Repeat password", "short")
        rule.onNodeWithText("OK").assertIsNotEnabled()

        field("Password").performTextInput("-but-now-long")
        field("Repeat password").performTextInput("-typo")
        waitForText("The passwords don't match")
        rule.onNodeWithText("OK").assertIsNotEnabled()
    }
}
