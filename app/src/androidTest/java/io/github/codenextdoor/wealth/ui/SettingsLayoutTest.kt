package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.settings.SETTINGS_ICON_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Every Settings row's icon sits in the middle of its row, also when the summary wraps. */
@RunWith(AndroidJUnit4::class)
class SettingsLayoutTest : UiTest() {

    @Test
    fun iconsAreCentredOnTheirRows() {
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Expense categories")
        val icons = rule.onAllNodes(hasTestTag(SETTINGS_ICON_TAG), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(icons.isNotEmpty())
        val onePixel = 1.5f
        icons.forEach { icon ->
            val row = generateSequence(icon.parent, SemanticsNode::parent).first { SemanticsActions.OnClick in it.config }
            // Unclipped positions: a row cut off at the screen's edge still counts.
            val iconCentre = icon.positionInRoot.y + icon.size.height / 2f
            val rowCentre = row.positionInRoot.y + row.size.height / 2f
            if (abs(iconCentre - rowCentre) > onePixel) logScreen()
            assertEquals("icon of the row at y=${row.positionInRoot.y}", rowCentre, iconCentre, onePixel)
        }
    }
}
