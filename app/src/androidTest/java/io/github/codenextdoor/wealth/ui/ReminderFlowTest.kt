package io.github.codenextdoor.wealth.ui

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.reminders.BalanceReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Balances not updated for a while: the Overview card, Settings › Reminders, the weekly notification. */
@RunWith(AndroidJUnit4::class)
class ReminderFlowTest : UiTest() {

    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val tag = System.nanoTime() % 100000
    private val name = "Stale $tag"

    private fun addOldAccount() = runBlocking {
        val type = container.catalogRepository.accountTypes.first().first { it.name == "Bank account" }
        container.accountRepository.save(
            Account(0, name, type.id, "CHF", type.countryId, 1_00, Instant.EPOCH, null, null),
            balanceDate = LocalDate.now().minusDays(200),
            recordBalance = true,
        )
    }

    @After
    fun clearNotifications() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        // Back to how the other tests expect it.
        container.reminderPreferences.setEnabled(false)
    }

    @Test
    fun theOverviewCardListsAnOldBalanceOpensItAndNotNowHidesIt() {
        addOldAccount()
        container.reminderPreferences.snoozeUntil(LocalDate.now().minusDays(1))
        container.reminderPreferences.setEnabled(true)
        openTab("Overview")
        val list = scrollingList()
        list.performScrollToNode(hasText(name))
        waitForText("Not updated for a while")
        rule.onNodeWithText(name).tap()
        waitForText("Edit account")
        Espresso.pressBack()
        list.performScrollToNode(hasText("Not now"))
        rule.onNodeWithText("Not now").tap()
        rule.waitUntil(10_000) { !isShown("Not updated for a while") }
        assertEquals(LocalDate.now().plusDays(7), container.reminderPreferences.snoozedUntil.value)
    }

    @Test
    fun settingsTurnRemindersOffAndChooseTheDays() {
        container.reminderPreferences.setEnabled(true)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("After").performScrollTo().performClick()
        rule.onNodeWithText("60 days").performScrollTo().tap()
        rule.waitUntil(5_000) { container.reminderPreferences.days.value == 60 }
        rule.onNodeWithText("Remind me to update balances").performScrollTo().tap()
        rule.waitUntil(5_000) { !container.reminderPreferences.enabled.value }
        container.reminderPreferences.setDays(35)
    }

    @Test
    fun theWeeklyCheckNotifiesHowManyWithoutNames() {
        addOldAccount()
        container.reminderPreferences.setEnabled(true)
        val result = runBlocking { TestListenableWorkerBuilder<BalanceReminderWorker>(context.applicationContext).build().doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications
        val text = shown.single().notification.extras.getCharSequence("android.text").toString()
        assertTrue(text, text.contains("haven't been updated in over 35 days") || text.contains("hasn't been updated in over 35 days"))
        assertTrue("an account name is in the notification", !text.contains(name))
    }
}
