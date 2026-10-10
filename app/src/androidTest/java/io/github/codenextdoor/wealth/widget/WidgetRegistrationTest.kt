package io.github.codenextdoor.wealth.widget

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The home-screen widget is offered by Android, sized as designed. */
@RunWith(AndroidJUnit4::class)
class WidgetRegistrationTest {

    @Test
    fun theWidgetIsOfferedWithItsDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ours = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.provider.packageName == context.packageName }
        assertEquals(listOf(WealthWidgetReceiver::class.java.name), ours.map { it.provider.className })
        val info = ours.single()
        assertTrue(info.loadDescription(context).toString().contains("Hidden while the eye is closed or the app lock is on"))
        assertEquals(4, info.targetCellWidth)
        assertEquals(2, info.targetCellHeight)
    }
}
