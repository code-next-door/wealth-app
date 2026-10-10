package io.github.codenextdoor.wealth.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.DarkColors
import io.github.codenextdoor.wealth.ui.theme.LightColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home-screen widget: net worth when small; with its change this month and this
 * month's spending when wide. Figures arrive masked while the eye is closed or the app
 * lock is on ([WidgetData]). Tapping it opens the app (behind the lock as always).
 */
class WealthWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as WealthApplication).container
        // The app's start opens the encrypted database; a key problem leaves only "open the app".
        val ready = withTimeoutOrNull(30_000) { container.databaseState.first { it != DatabaseState.Opening } } == DatabaseState.Ready
        val data = if (ready) container.widgetData.data.first() else null
        val wallpaperColors = container.appearancePreferences.useWallpaperColors.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        provideContent {
            if (wallpaperColors) {
                GlanceTheme { WidgetContent(data) }
            } else {
                GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) { WidgetContent(data) }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class WealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WealthWidget()
}

/** [data] null: the figures can't be read now (e.g. before the database opens). */
@Composable
fun WidgetContent(data: WidgetData?) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WealthWidget.WIDE.width && LocalSize.current.height >= WealthWidget.WIDE.height
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.primary),
                modifier = GlanceModifier.size(22.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(context.getString(R.string.widget_net_worth), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 14.sp))
        }
        when {
            data == null -> Text(context.getString(R.string.widget_open_app), style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
            !data.hasAccounts -> Text(context.getString(R.string.widget_no_accounts), style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
            else -> {
                Text(
                    data.netWorthText,
                    style = TextStyle(color = colors.onSurface, fontSize = if (wide) 30.sp else 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (wide) WideDetails(data)
            }
        }
    }
}

@Composable
private fun WideDetails(data: WidgetData) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    if (data.hidden) {
        Spacer(GlanceModifier.height(8.dp))
        Text(context.getString(R.string.widget_tap_to_open), style = TextStyle(color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        return
    }
    data.change?.let { change ->
        // Arrow and sign carry the direction, never colour alone.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(if (change.isIncrease) R.drawable.ic_arrow_upward else R.drawable.ic_arrow_downward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.primary),
                modifier = GlanceModifier.size(16.dp),
            )
            Text(
                listOfNotNull(change.amountText, change.percentText?.let { "($it)" }, context.getString(R.string.widget_this_month)).joinToString(" "),
                style = TextStyle(color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
    Spacer(GlanceModifier.height(8.dp))
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            context.getString(R.string.widget_spent_in, data.month.atDay(1).format(localDateFormat("MMMM"))),
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(data.spentText, style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
