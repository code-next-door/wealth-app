package io.github.codenextdoor.wealth.releasetest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Smoke tests of the shrunk app: each one starts from a fresh install state and
 * touches a different part that R8 could break (encrypted database, Room queries,
 * the rule matcher, Compose screens, PIN hashing, the lock).
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBuildTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun freshStart() {
        // Wipes only the minified copy's data, as if it had just been installed.
        device.executeShellCommand("pm clear $PACKAGE")
        launch()
        find(By.text("Overview"))
    }

    @Test
    fun opensOnAnEmptyDashboard() {
        find(By.textContains("Add your accounts"))
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
        // Reopening it passes its id in the screen's route (kept by R8).
        find(By.text("TWINT *MIGROS ZURICH")).click()
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

    private fun launch() {
        // The launcher intent, so an existing app instance is brought back rather than duplicated.
        device.executeShellCommand("monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1")
    }

    private fun find(selector: BySelector, timeoutMs: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs)
            ?: fail("Not on screen within $timeoutMs ms: $selector")

    /** Waits for [selector], scrolling the screen's list down if it isn't in view. */
    private fun scrollTo(selector: BySelector): UiObject2 {
        device.wait(Until.findObject(selector), 3_000)?.let { return it }
        find(By.scrollable(true))
        // The page's list, not a scrollable text field on it.
        val list = device.findObjects(By.scrollable(true)).maxBy { it.visibleBounds.height() }
        return list.scrollUntil(Direction.DOWN, Until.findObject(selector))
            ?: fail("Not found after scrolling: $selector")
    }

    /** Logs what was on screen (logcat tag "ReleaseBuildTest"), then fails. */
    private fun fail(message: String): Nothing {
        val screen = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
        screen.chunked(3_000).forEach { Log.i("ReleaseBuildTest", it) }
        throw AssertionError("$message (screen logged under tag ReleaseBuildTest)")
    }

    private fun waitForFields(count: Int): List<UiObject2> {
        val editText = By.clazz("android.widget.EditText")
        device.wait({ device.findObjects(editText).size >= count }, 10_000)
        return device.findObjects(editText).also { check(it.size >= count) { "expected $count text fields" } }
    }

    /** The switch or option button labelled [label], or in the same row as it. */
    private fun checkableNextTo(label: String): UiObject2 {
        var node = find(By.text(label))
        while (!node.isCheckable) {
            node.findObject(By.checkable(true))?.let { return it }
            node = node.parent ?: error("nothing to switch next to $label")
        }
        return node
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit -> find(By.text(digit.toString())).click() }
        find(By.text("OK")).click()
    }

    private companion object {
        const val PACKAGE = "io.github.codenextdoor.wealth.minified"
    }
}
