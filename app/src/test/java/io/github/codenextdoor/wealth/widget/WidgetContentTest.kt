package io.github.codenextdoor.wealth.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.dashboard.Delta
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** The widget's two sizes, and its hidden and not-ready states. */
@RunWith(AndroidJUnit4::class)
class WidgetContentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val shown = WidgetData(
        hasAccounts = true,
        hidden = false,
        netWorthText = "CHF 122,340.00",
        change = Delta("+CHF 2,340.00", "+1.9%", isIncrease = true),
        month = YearMonth.of(2026, 10),
        spentText = "CHF 1,820.00",
    )

    @Test
    fun wideShowsNetWorthItsChangeAndSpending() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(320.dp, 160.dp))
        provideComposable { WidgetContent(shown) }
        onNode(hasText("CHF 122,340.00")).assertExists()
        onNode(hasText("+CHF 2,340.00")).assertExists()
        onNode(hasText("Spent in October")).assertExists()
        onNode(hasText("CHF 1,820.00")).assertExists()
    }

    @Test
    fun smallShowsNetWorthOnly() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(160.dp, 80.dp))
        provideComposable { WidgetContent(shown) }
        onNode(hasText("CHF 122,340.00")).assertExists()
        onNode(hasText("Spent in October")).assertDoesNotExist()
    }

    @Test
    fun hiddenSaysTapToOpen() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(320.dp, 160.dp))
        provideComposable { WidgetContent(shown.copy(hidden = true, netWorthText = "CHF ••••", change = null, spentText = "CHF ••••")) }
        onNode(hasText("CHF ••••")).assertExists()
        onNode(hasText("Tap to open")).assertExists()
        onNode(hasText("Spent in October")).assertDoesNotExist()
    }

    @Test
    fun notReadyAsksToOpenTheApp() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(320.dp, 160.dp))
        provideComposable { WidgetContent(null) }
        onNode(hasText("Open Wealth to see your figures")).assertExists()
    }
}
