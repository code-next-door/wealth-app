package io.github.codenextdoor.wealth.releasetest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An update must keep everything: data made with an older release has to open
 * in this one (database key, PIN, theme, the encrypted data). Skipped in normal
 * runs; `releasetest/upgrade-check.sh <old tag>` installs that release's
 * shrunk build, runs [makeDataInTheOldVersion], installs this build over it
 * (like an update from GitHub) and runs [findItAfterTheUpdate].
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest : AppUiTest() {

    private val step = InstrumentationRegistry.getArguments().getString("upgradeStep")

    @Test
    fun makeDataInTheOldVersion() {
        assumeTrue(step == "before")
        device.executeShellCommand("pm clear $PACKAGE")
        launch()
        skipWelcomeIfShown()
        find(By.text("Spending")).click()
        find(By.desc("Add expense")).click()
        val fields = waitForFields(2)
        fields[0].text = "UPGRADE CHECK"
        fields[1].text = "12.50"
        find(By.text("Save")).click()
        find(By.text("UPGRADE CHECK"))

        find(By.desc("Settings")).click()
        scrollTo(By.text("Dark"))
        checkableNextTo("Dark").click()
        check(device.wait({ checkableNextTo("Dark").isChecked }, 5_000)) { "Dark not chosen" }
        scrollTo(By.text("App lock"))
        checkableNextTo("App lock").click()
        find(By.text("Choose a PIN"))
        enterPin(PIN)
        find(By.text("Enter the PIN again"))
        enterPin(PIN)
        device.wait(Until.gone(By.text("Enter the PIN again")), 10_000)
        check(checkableNextTo("App lock").isChecked) { "lock not on" }
    }

    @Test
    fun findItAfterTheUpdate() {
        assumeTrue(step == "after")
        launch()
        // The update restarted the app, so it starts locked, with the old PIN.
        find(By.text("Wealth is locked"))
        enterPin(PIN)
        // The database opens with the key made by the old version.
        find(By.text("Spending")).click()
        scrollTo(By.text("UPGRADE CHECK")) // below the year view since 0.4
        find(By.desc("Settings")).click()
        scrollTo(By.text("Dark"))
        check(checkableNextTo("Dark").isChecked) { "theme not kept" }
    }

    private companion object {
        const val PIN = "2468"
    }
}
