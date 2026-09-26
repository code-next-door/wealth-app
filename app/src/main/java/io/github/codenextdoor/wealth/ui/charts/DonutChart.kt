package io.github.codenextdoor.wealth.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * Donut chart for part-to-whole at a glance. Keep it to six segments or
 * fewer and always pair it with a legend that lists the values.
 */
@Composable
fun DonutChart(
    fractions: List<Float>,
    colors: List<Color>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    thickness: Dp = 24.dp,
) {
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val strokePx = thickness.toPx()
        val diameter = size.minDimension - strokePx
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        // A small gap between segments separates them without borders.
        val gapDegrees = if (fractions.size > 1) (2.dp.toPx() / (PI.toFloat() * diameter)) * 360f else 0f
        var start = -90f
        fractions.forEachIndexed { i, fraction ->
            val sweep = fraction * 360f
            drawArc(
                color = colors[i],
                startAngle = start + gapDegrees / 2,
                sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokePx),
            )
            start += sweep
        }
    }
}
