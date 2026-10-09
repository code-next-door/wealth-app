package io.github.codenextdoor.wealth.ui

import android.os.ParcelFileDescriptor
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesElements
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.data.preferences.ThemeMode
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen (Home's tabs and each journey's screen) passes the Accessibility Test
 * Framework's checks (touch target size, contrast, labels), in the normal setting and
 * at the largest text in dark theme. At the largest text each screen must also still
 * open and show its title.
 *
 * Compose's accessibility checks need Android 14+; on older devices this test is skipped.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ScreenChecksTest : ScreenJourneys() {

    private var themeBefore: ThemeMode? = null
    private var fontScaleBefore: String? = null

    @After
    fun restoreDisplay() {
        themeBefore?.let { container.appearancePreferences.setThemeMode(it) }
        fontScaleBefore?.let { setFontScale(it) }
    }

    @Test
    fun everyScreenPassesAccessibilityChecks() = checkEveryScreen()

    @Test
    fun everyScreenPassesAccessibilityChecksAtTheLargestTextInDarkTheme() {
        themeBefore = container.appearancePreferences.themeMode.value
        container.appearancePreferences.setThemeMode(ThemeMode.DARK)
        fontScaleBefore = shell("settings get system font_scale").trim().takeIf { it != "null" && it.isNotEmpty() } ?: "1.0"
        setFontScale("2.0")
        rule.activityRule.scenario.recreate()
        checkEveryScreen()
    }

    private fun checkEveryScreen() {
        rule.enableAccessibilityChecks(
            AccessibilityValidator().setSuppressingResultMatcher(
                allOf(matchesCheck(SpeakableTextPresentCheck::class.java), matchesElements(slivers)),
            ),
        )
        HOME_TABS.forEach { (tab, title) ->
            openTab(tab)
            awaitTitle(title, "Home › $tab")
            check("Home › $tab")
        }
        journeys.forEach { journey ->
            awaitTitle(HOME_TITLES)
            journey.open()
            awaitTitle(journey.title, "opening ${journey.route}")
            check(journey.title)
            back()
            backToHome()
        }
    }

    private fun check(screen: String) {
        try {
            rule.onRoot().tryPerformAccessibilityChecks()
        } catch (e: Throwable) {
            logScreen()
            throw AssertionError("$screen: ${e.message}", e)
        }
    }

    private fun setFontScale(scale: String) {
        shell("settings put system font_scale $scale")
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
    }

    /**
     * A list row scrolled almost entirely under the bottom bar: the checker sees only a
     * few pixels without its text and reports "no label" (screen readers still read the
     * whole row). Only that report, only for slivers this thin.
     */
    private val slivers = object : TypeSafeMatcher<ViewHierarchyElement>() {
        override fun matchesSafely(element: ViewHierarchyElement) = element.boundsInScreen.height < SLIVER_PX
        override fun describeTo(description: Description) {
            description.appendText("element less than $SLIVER_PX px tall on screen")
        }
    }

    private companion object {
        const val SLIVER_PX = 8

        val HOME_TABS = listOf("Overview" to "Wealth", "Accounts" to "Accounts", "House" to "House", "Spending" to "Spending")
    }
}
