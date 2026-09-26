package io.github.codenextdoor.wealth.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One point on a line chart. [value] is only used for drawing; [valueText] is shown to the user. */
data class ChartPoint(
    val date: LocalDate,
    val value: Float,
    val valueText: String,
)

/**
 * Line chart of [history] (solid) followed by an optional [forecast] (dashed,
 * starting where history ends). Touch and drag to read exact values.
 */
@Composable
fun LineChart(
    history: List<ChartPoint>,
    forecast: List<ChartPoint>,
    formatAxisValue: (Float) -> String,
    formatDate: (LocalDate) -> String,
    projectedLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return
    val all = history + forecast
    val lineColor = ChartColors.series(0)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = tooltipText)
    val measurer = rememberTextMeasurer()
    var selected by remember(all) { mutableStateOf<Int?>(null) }

    // Round gridline values (e.g. 5,000 / 10,000 / 15,000) that bracket the data.
    val ticks = remember(all) { niceTicks(all.minOf { it.value }, all.maxOf { it.value }) }
    val yMin = ticks.first()
    val yMax = ticks.last()
    val firstDay = all.first().date.toEpochDay()
    val daySpan = (all.last().date.toEpochDay() - firstDay).coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(all) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun nearest(x: Float): Int {
                        val day = firstDay + (x / size.width) * daySpan
                        return all.indices.minBy { abs(all[it].date.toEpochDay() - day) }
                    }
                    selected = nearest(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        selected = nearest(change.position.x)
                    }
                    selected = null
                }
            },
    ) {
        val labelHeight = 16.dp.toPx()
        val plotTop = 4.dp.toPx()
        val plotBottom = size.height - labelHeight - 4.dp.toPx()
        fun x(date: LocalDate) = (date.toEpochDay() - firstDay).toFloat() / daySpan * size.width
        fun y(value: Float) = plotTop + (1f - (value - yMin) / (yMax - yMin)) * (plotBottom - plotTop)

        // Recessive gridlines, each labelled at its left end.
        ticks.forEach { value ->
            val gy = y(value)
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            drawText(measurer, formatAxisValue(value), Offset(0f, gy - labelHeight), style = labelStyle)
        }

        // Start and end dates under the plot.
        val startLabel = measurer.measure(formatDate(all.first().date), labelStyle)
        val endLabel = measurer.measure(formatDate(all.last().date), labelStyle)
        drawText(startLabel, topLeft = Offset(0f, size.height - startLabel.size.height))
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, size.height - endLabel.size.height))

        val stroke = 2.dp.toPx()
        fun pathOf(points: List<ChartPoint>) = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.value)) else lineTo(x(p.date), y(p.value)) }
        }
        drawPath(pathOf(history), lineColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (forecast.isNotEmpty()) {
            drawPath(
                pathOf(listOf(history.last()) + forecast),
                lineColor,
                style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
        }

        // Marker on today's value, with a ring in the surface color so it reads over the line.
        val last = history.last()
        drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(last.date), y(last.value)))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(last.date), y(last.value)))

        selected?.let { index ->
            val point = all[index]
            val px = x(point.date)
            val py = y(point.value)
            drawLine(gridColor, Offset(px, plotTop), Offset(px, plotBottom), strokeWidth = 1.dp.toPx())
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(px, py))
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(px, py))

            val isProjected = index >= history.size
            val text = buildString {
                append(formatDate(point.date))
                if (isProjected) append(" · ").append(projectedLabel)
                append('\n').append(point.valueText)
            }
            val layout = measurer.measure(text, tooltipStyle)
            val pad = 8.dp.toPx()
            val boxSize = Size(layout.size.width + pad * 2, layout.size.height + pad * 2)
            val boxX = (px - boxSize.width / 2).coerceIn(0f, size.width - boxSize.width)
            val boxY = if (py - boxSize.height - 12.dp.toPx() > 0) py - boxSize.height - 12.dp.toPx() else py + 12.dp.toPx()
            drawRoundRect(tooltipBackground, Offset(boxX, boxY), boxSize, CornerRadius(8.dp.toPx()))
            drawText(layout, topLeft = Offset(boxX + pad, boxY + pad))
        }
    }
}

/**
 * Three to five evenly spaced "round" values (steps of 1, 2 or 5 × 10^n)
 * covering [min]..[max]. The first and last are the chart's y range.
 */
internal fun niceTicks(min: Float, max: Float): List<Float> {
    val span = (max - min).takeIf { it > 0f } ?: maxOf(abs(max), 1f)
    val rough = span / 3f
    val magnitude = 10f.pow(floor(log10(rough)))
    val step = listOf(1f, 2f, 5f, 10f).map { it * magnitude }.first { it >= rough }
    val low = floor(min / step) * step
    val high = ceil(max / step) * step
    val count = ((high - low) / step).toInt().coerceAtLeast(1)
    return (0..count).map { low + it * step }.let { if (low == high) listOf(low - step, low, low + step) else it }
}
