package io.github.codenextdoor.wealth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Checks that the StrictMode watch really catches slow main-thread work, so the UI tests' check means something. */
@RunWith(AndroidJUnit4::class)
class StrictModeWatchTest {

    @Test
    fun aDiskReadOnTheMainThreadIsCaught() {
        StrictModeViolations.takeAll()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            File(instrumentation.targetContext.filesDir, "strict-mode-check").exists()
        }
        val found = StrictModeViolations.takeAll()
        assertTrue("nothing caught", found.any { it.startsWith("DiskReadViolation") })
    }
}
