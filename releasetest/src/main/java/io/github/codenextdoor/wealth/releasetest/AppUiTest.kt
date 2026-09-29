package io.github.codenextdoor.wealth.releasetest

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream

/** Drives the shrunk app (package [PACKAGE]) through its screens with UI Automator. */
abstract class AppUiTest {

    protected val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** A wiped app is a fresh install: its welcome screen comes first (not in releases before 0.4). */
    protected fun skipWelcomeIfShown() {
        // Shown once the new database is set up; skip it only if it's there.
        device.wait(Until.findObject(By.text("Welcome to Wealth")), 15_000) ?: return
        scrollTo(By.text("Skip")).click()
        device.wait(Until.gone(By.text("Welcome to Wealth")), 10_000)
    }

    protected fun launch() {
        // The launcher intent, so an existing app instance is brought back rather than duplicated.
        device.executeShellCommand("monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1")
    }

    protected fun find(selector: BySelector, timeoutMs: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs)
            ?: fail("Not on screen within $timeoutMs ms: $selector")

    /** Waits for [selector], scrolling the screen's list down if it isn't in view. */
    protected fun scrollTo(selector: BySelector): UiObject2 {
        device.wait(Until.findObject(selector), 3_000)?.let { return it }
        find(By.scrollable(true))
        // The page's list, not a scrollable text field on it.
        val list = device.findObjects(By.scrollable(true)).maxBy { it.visibleBounds.height() }
        return list.scrollUntil(Direction.DOWN, Until.findObject(selector))
            ?: fail("Not found after scrolling: $selector")
    }

    /** Logs what was on screen (logcat tag "ReleaseBuildTest"), then fails. */
    protected fun fail(message: String): Nothing {
        val screen = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
        screen.chunked(3_000).forEach { Log.i("ReleaseBuildTest", it) }
        throw AssertionError("$message (screen logged under tag ReleaseBuildTest)")
    }

    protected fun waitForFields(count: Int): List<UiObject2> {
        val editText = By.clazz("android.widget.EditText")
        device.wait({ device.findObjects(editText).size >= count }, 10_000)
        return device.findObjects(editText).also { check(it.size >= count) { "expected $count text fields" } }
    }

    /** The switch or option button labelled [label], or in the same row as it. */
    protected fun checkableNextTo(label: String): UiObject2 {
        var node = find(By.text(label))
        while (!node.isCheckable) {
            node.findObject(By.checkable(true))?.let { return it }
            node = node.parent ?: error("nothing to switch next to $label")
        }
        return node
    }

    protected fun enterPin(pin: String) {
        pin.forEach { digit -> find(By.text(digit.toString())).click() }
        find(By.text("OK")).click()
    }

    protected companion object {
        const val PACKAGE = "io.github.codenextdoor.wealth.minified"
    }
}
