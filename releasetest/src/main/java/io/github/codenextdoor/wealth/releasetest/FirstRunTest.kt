package io.github.codenextdoor.wealth.releasetest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

/** A fresh install of the shrunk app: welcome screen, the whole tour, then the checklist. */
@RunWith(AndroidJUnit4::class)
class FirstRunTest : AppUiTest() {

    @Test
    fun welcomeTourAndChecklist() {
        device.executeShellCommand("pm clear $PACKAGE")
        launch()
        find(By.text("Welcome to Wealth"), timeoutMs = 20_000)
        scrollTo(By.text("Show me around")).click()
        for (stop in 1..6) {
            find(By.text("$stop of 6"))
            find(By.text(if (stop < 6) "Next" else "Done")).click()
        }
        check(device.wait(Until.gone(By.text("6 of 6")), 5_000)) { "tour still showing" }
        find(By.text("Getting started"))
    }
}
