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
import io.github.codenextdoor.wealth.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon

data class LegendEntry(
    val label: String,
    val amountText: String,
    val percentText: String,
    val fraction: Float,
    val color: Color,
    /** For a folded bucket ("Other"): what's inside. Tapping the row opens and closes it. */
    val parts: List<LegendEntry> = emptyList(),
)

/**
 * Donut chart with a total in the middle and a legend that doubles as the
 * table view (every value readable without color). When [onSelect] is set,
 * legend rows are tappable and [selected] is emphasized. A row with
 * [LegendEntry.parts] opens to list them instead (open while one is
 * [selectedPart]); with [onSelectPart], each part is tappable too.
 */
@Composable
fun DonutWithLegend(
    entries: List<LegendEntry>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    selectedPart: Pair<Int, Int>? = null,
    onSelectPart: ((entry: Int, part: Int) -> Unit)? = null,
) {
    var opened by remember { mutableStateOf(emptySet<Int>()) }
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
            if (entry.parts.isEmpty()) {
                LegendRow(entry, entry.color, isSelected = selected == i, onClick = onSelect?.let { { it(i) } })
            } else {
                val open = i in opened || selectedPart?.first == i
                LegendRow(
                    entry,
                    entry.color,
                    isSelected = selected == i,
                    onClick = { opened = if (i in opened) opened - i else opened + i },
                    open = open,
                )
                if (open) {
                    entry.parts.forEachIndexed { j, part ->
                        LegendRow(
                            part,
                            entry.color,
                            isSelected = selectedPart == (i to j),
                            onClick = onSelectPart?.let { { it(i, j) } },
                            indent = true,
                        )
                    }
                }
            }
        }
    }
}

/** One legend line: dot, label, amount, percent. [open] non-null adds an open/close arrow. */
@Composable
private fun LegendRow(
    entry: LegendEntry,
    color: Color,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    open: Boolean? = null,
    indent: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 18.dp else 0.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Canvas(Modifier.size(if (indent) 6.dp else 10.dp)) { drawCircle(color) }
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
        open?.let {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(if (it) R.string.legend_hide_parts else R.string.legend_show_parts),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(18.dp)
                    .rotate(if (it) -90f else 90f),
            )
        }
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
