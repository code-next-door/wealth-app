package io.github.codenextdoor.wealth

import android.os.StrictMode
import android.os.strictmode.Violation
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Makes StrictMode findings part of the device tests: slow work on the main
 * thread (disk, database) and leaked resources in *our* code are recorded, and
 * UI tests fail if any happened (see UiTest). Violations only from Android or
 * test libraries (e.g. their own garbage collection calls) are ignored.
 */
object StrictModeViolations {
    private const val APP_PACKAGE = "io.github.codenextdoor.wealth"
    private val found = CopyOnWriteArrayList<String>()
    private val executor = Executors.newSingleThreadExecutor()

    /** Call on the main thread (thread policies apply to the thread that sets them). */
    fun watch() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().penaltyListener(executor, ::record).build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().penaltyListener(executor, ::record).build())
    }

    private fun record(violation: Violation) {
        val ours = violation.stackTrace.firstOrNull { it.className.startsWith(APP_PACKAGE) && !it.className.contains(".StrictModeViolations") }
            ?: return
        found += "${violation.javaClass.simpleName} at $ours"
    }

    /** Waits a moment for findings still on their way, then returns and clears them. */
    fun takeAll(): List<String> {
        Thread.sleep(200) // The listener reports on its own thread.
        return found.toList().also { found.clear() }
    }
}
