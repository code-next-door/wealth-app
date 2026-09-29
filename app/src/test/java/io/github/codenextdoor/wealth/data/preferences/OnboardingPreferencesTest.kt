package io.github.codenextdoor.wealth.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "onboarding_${System.nanoTime()}"

    @Test
    fun nothingShowsUntilSettled() {
        val prefs = OnboardingPreferences(SettingsStore(context, name))
        assertFalse(prefs.settled.value)
    }

    @Test
    fun aFreshInstallGetsTheFirstRunHelp() {
        val prefs = OnboardingPreferences(SettingsStore(context, name))
        prefs.settle(freshInstall = true)
        assertTrue(prefs.settled.value)
        assertFalse(prefs.welcomeDone.value)
        assertFalse(prefs.checklistDismissed.value)
    }

    @Test
    fun someoneUpdatingWithTheirDataNeverSeesIt() {
        val prefs = OnboardingPreferences(SettingsStore(context, name))
        prefs.settle(freshInstall = false)
        assertTrue(prefs.welcomeDone.value)
        assertTrue(prefs.checklistDismissed.value)
    }

    @Test
    fun itSettlesOnceAndIsRememberedFromDisk() {
        val store = SettingsStore(context, name)
        val prefs = OnboardingPreferences(store)
        prefs.settle(freshInstall = true)
        prefs.setWelcomeDone()
        prefs.setBackupMade()
        prefs.settle(freshInstall = true) // later starts change nothing
        assertTrue(prefs.welcomeDone.value)
        store.close()

        val reopened = OnboardingPreferences(SettingsStore(context, name))
        assertTrue(reopened.settled.value && reopened.welcomeDone.value && reopened.backupMade.value)
        assertFalse(reopened.checklistDismissed.value)
    }
}
