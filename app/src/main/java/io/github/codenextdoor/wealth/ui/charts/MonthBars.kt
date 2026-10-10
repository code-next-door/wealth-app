package io.github.codenextdoor.wealth.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One bar: its short label under it ("M"), what a screen reader says, its height (0–1). */
data class Bar(val label: String, val description: String, val fraction: Float)

/**
 * Bars side by side (e.g. 12 months); a tap picks the bar under it. The chart is one
 * touch target (12 bars can't each be 48 dp wide on a phone); screen readers get one
 * action per bar instead. The selected bar is outlined as well as darker, so it isn't
 * told by colour alone.
 */
@Composable
fun BarChart(bars: List<Bar>, selected: Int?, onSelect: (Int) -> Unit, description: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(bars.size) {
                detectTapGestures { offset ->
                    if (bars.isNotEmpty()) onSelect((offset.x / size.width * bars.size).toInt().coerceIn(0, bars.size - 1))
                }
            }
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(description, selected?.let { bars.getOrNull(it)?.description }).joinToString(". ")
                customActions = bars.mapIndexed { index, bar -> CustomAccessibilityAction(bar.description) { onSelect(index); true } }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == selected
            val dimmed = selected != null && !isSelected
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (bar.fraction > 0f) {
                        val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(bar.fraction.coerceIn(0.02f, 1f))
                                .background(primary.copy(alpha = if (dimmed) 0.35f else 1f), shape)
                                .then(if (isSelected) Modifier.border(2.dp, outline, shape) else Modifier),
                        )
                    }
                }
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
