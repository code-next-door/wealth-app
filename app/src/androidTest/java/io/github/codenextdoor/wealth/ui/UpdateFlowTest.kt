package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.TestUpdates
import io.github.codenextdoor.wealth.data.update.Release
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A newer release on "GitHub" (a fake): offered in a dialog; Later and a failed download. */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest : UiTest() {

    private val newer = Release("9.9.9", "https://example.invalid/release", "https://example.invalid/wealth.apk", "https://example.invalid/wealth.apk.sha256")

    @After
    fun noRelease() {
        TestUpdates.release = null
        container.updateChecker.dismiss()
        container.updateChecker.setEnabled(true)
    }

    private fun offer() {
        TestUpdates.release = newer
        runBlocking { container.updateChecker.checkNow() }
        waitForText("Update available")
        assertTrue(isShown("Wealth 9.9.9 is out (you have 1.0.0)", substring = true))
    }

    @Test
    fun laterClosesTheDialog() {
        offer()
        rule.onNodeWithText("Later").tap()
        rule.waitUntil(5_000) { !isShown("Update available") }
    }

    @Test
    fun aFailedDownloadOffersTheReleasePage() {
        offer()
        rule.onNodeWithText("Update").tap()
        waitForText("Open release page")
        rule.onNodeWithText("Later").tap()
        rule.waitUntil(5_000) { !isShown("Update available") }
    }

    @Test
    fun settingsCanTurnTheCheckOffAndShowTheVersion() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Check for updates").performScrollTo().tap()
        rule.waitUntil(5_000) { !container.updateChecker.enabled.value }
        assertTrue(isShown("This version: 1.0.0"))
        rule.onNodeWithText("Check for updates").tap()
        rule.waitUntil(5_000) { container.updateChecker.enabled.value }
        assertFalse(isShown("Update available"))
    }
}
