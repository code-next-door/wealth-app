package io.github.codenextdoor.wealth.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation map: every screen is reached by tapping from Home, survives the
 * activity being recreated, and Back lands where it should.
 *
 * Complete by construction: a screen in [Routes] without a journey in
 * [ScreenJourneys] fails [everyScreenHasAJourney].
 */
@RunWith(AndroidJUnit4::class)
class NavigationMapTest : ScreenJourneys() {

    /** Opened from another app ("Open with", "Share"), not by tapping: see OpenWithFlowTest. */
    private val fromOtherApps = setOf("ImportFile", "BackfillFiles")

    @Test
    fun everyScreenHasAJourney() {
        val all = Routes::class.java.declaredClasses.filter { Route::class.java.isAssignableFrom(it) }.map { it.simpleName }.toSet()
        val covered = journeys.map { it.route }.toSet() + "Home"
        assertEquals("screens without a journey in the navigation map", emptySet<String>(), all - covered - legacyRoutes - fromOtherApps)
        assertEquals("journeys or legacy routes that no longer exist", emptySet<String>(), (covered + legacyRoutes + fromOtherApps) - all)
    }

    @Test
    fun everyJourneyOpensItsScreenSurvivesRecreationAndGoesBack() {
        journeys.forEach { journey ->
            awaitTitle(HOME_TITLES)
            journey.open()
            awaitTitle(journey.title, "opening ${journey.route}")
            rule.activityRule.scenario.recreate()
            awaitTitle(journey.title, "${journey.route} after the activity was recreated")
            back()
            awaitTitle(journey.backTo, "Back from ${journey.route}")
            backToHome()
        }
    }
}
