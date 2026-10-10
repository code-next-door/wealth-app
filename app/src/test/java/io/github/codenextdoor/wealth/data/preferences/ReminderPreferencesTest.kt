package io.github.codenextdoor.wealth.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReminderPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "reminders_${System.nanoTime()}"

    @Test
    fun onWith35DaysUntilChanged() {
        val prefs = ReminderPreferences(SettingsStore(context, name))
        assertTrue(prefs.enabled.value)
        assertEquals(35, prefs.days.value)
        assertNull(prefs.snoozedUntil.value)
    }

    @Test
    fun choicesAreKept() {
        val store = SettingsStore(context, name)
        val prefs = ReminderPreferences(store)
        prefs.setEnabled(false)
        prefs.setDays(60)
        prefs.snoozeUntil(LocalDate.of(2026, 10, 17))
        runBlocking { store.close() }

        val again = ReminderPreferences(SettingsStore(context, name))
        assertFalse(again.enabled.value)
        assertEquals(60, again.days.value)
        assertEquals(LocalDate.of(2026, 10, 17), again.snoozedUntil.value)
    }

    @Test
    fun monthlyReviewOnUntilChangedAndItsMonthsAreKept() {
        val store = SettingsStore(context, name)
        val prefs = ReminderPreferences(store)
        assertTrue(prefs.reviewEnabled.value)
        assertNull(prefs.reviewNotified)
        assertNull(prefs.reviewCardHidden.value)
        prefs.setReviewEnabled(false)
        prefs.setReviewNotified(java.time.YearMonth.of(2026, 9))
        prefs.hideReviewCard(java.time.YearMonth.of(2026, 9))
        runBlocking { store.close() }

        val again = ReminderPreferences(SettingsStore(context, name))
        assertFalse(again.reviewEnabled.value)
        assertEquals(java.time.YearMonth.of(2026, 9), again.reviewNotified)
        assertEquals(java.time.YearMonth.of(2026, 9), again.reviewCardHidden.value)
    }
}
