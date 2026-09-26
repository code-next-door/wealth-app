package io.github.codenextdoor.wealth.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class LegendEntry(
    val label: String,
    val amountText: String,
    val percentText: String,
    val fraction: Float,
    val color: Color,
)

/**
 * Donut chart with a total in the middle and a legend that doubles as the
 * table view (every value readable without color). When [onSelect] is set,
 * legend rows are tappable and [selected] is emphasized.
 */
@Composable
fun DonutWithLegend(
    entries: List<LegendEntry>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            DonutChart(
                fractions = entries.map { it.fraction },
                colors = entries.mapIndexed { i, e -> if (selected == null || selected == i) e.color else e.color.copy(alpha = 0.3f) },
                contentDescription = entries.joinToString { "${it.label} ${it.percentText}" },
                modifier = Modifier.size(180.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        entries.forEachIndexed { i, entry ->
            val isSelected = selected == i
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        MaterialTheme.shapes.small,
                    )
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Canvas(Modifier.size(10.dp)) { drawCircle(entry.color) }
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(entry.amountText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    entry.percentText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp),
                )
            }
        }
    }
}
