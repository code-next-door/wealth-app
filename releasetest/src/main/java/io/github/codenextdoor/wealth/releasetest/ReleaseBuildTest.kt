package io.github.codenextdoor.wealth.releasetest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests of the shrunk app: each one starts from a fresh install state and
 * touches a different part that R8 could break (encrypted database, Room queries,
 * the rule matcher, Compose screens, PIN hashing, the lock).
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBuildTest : AppUiTest() {

    @Before
    fun freshStart() {
        // Wipes only the minified copy's data, as if it had just been installed.
        device.executeShellCommand("pm clear $PACKAGE")
        launch()
        skipWelcomeIfShown()
        find(By.text("Overview"))
    }

    @Test
    fun opensOnAnEmptyDashboard() {
        // A fresh install: the getting-started checklist, with its first step.
        find(By.text("Getting started"))
        find(By.text("Add your first account"))
    }

    @Test
    fun addsAnExpenseThatARuleCategorizes() {
        find(By.text("Spending")).click()
        find(By.desc("Add expense")).click()
        val fields = waitForFields(2)
        fields[0].text = "TWINT *MIGROS ZURICH"
        fields[1].text = "12.50"
        // The rule comes from the encrypted database, matched by the categorizer.
        find(By.textContains("Picked by the rule"))
        find(By.text("Save")).click()

        find(By.desc("Add expense"))
        // Reopening it passes its id in the screen's route (kept by R8). It's below the year view.
        scrollTo(By.text("TWINT *MIGROS ZURICH")).click()
        find(By.text("Edit expense"))
        find(By.text("TWINT *MIGROS ZURICH"))
    }

    @Test
    fun settingsScreensShowTheDefaults() {
        find(By.desc("Settings")).click()
        listOf(
            "Currencies & exchange rates" to "CHF",
            "Expense categories" to "Groceries",
            // Near the top: UI Automator can't scroll all the way through the one tall card of rules.
            "Categorization rules" to "ALDI",
            "Countries" to "Switzerland",
        ).forEach { (screen, shows) ->
            scrollTo(By.text(screen)).click()
            scrollTo(By.textContains(shows))
            device.pressBack()
            find(By.text("Settings"))
        }
    }

    @Test
    fun appLockAsksForThePinAfterLeaving() {
        find(By.desc("Settings")).click()
        scrollTo(By.text("App lock"))
        checkableNextTo("App lock").click()
        find(By.text("Choose a PIN"))
        enterPin("2468")
        find(By.text("Enter the PIN again"))
        enterPin("2468")
        device.wait(Until.gone(By.text("Enter the PIN again")), 10_000)
        scrollTo(By.text("Immediately"))
        checkableNextTo("Immediately").click()
        check(device.wait({ checkableNextTo("Immediately").isChecked }, 5_000)) { "Immediately not chosen" }

        device.pressHome()
        // The app counts as "left" a moment after it goes to the background.
        Thread.sleep(2_000)
        launch()
        find(By.text("Wealth is locked"))
        enterPin("1111")
        find(By.text("Wrong PIN. Try again."))
        enterPin("2468")
        // Unlocking returns to where the app was left.
        find(By.text("App lock"))
        check(device.findObject(By.text("Wealth is locked")) == null) { "still locked" }
    }
}
