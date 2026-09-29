package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.figure
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.time.YearMonth

/**
 * The year above the month view: twelve tiles with each month's spending,
 * shaded darker for bigger months (one hue; the text carries the value).
 * Tapping a month shows it; the arrows browse other years.
 */
@Composable
fun YearCalendar(
    state: YearUiState,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
) {
    if (state.isLoading) return
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousYear, enabled = state.canGoBack) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.expenses_previous_year))
            }
            Text(
                state.totalText?.let { stringResource(R.string.expenses_year_total, state.year, it.figure()) } ?: state.year.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextYear, enabled = state.canGoForward) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.expenses_next_year))
            }
        }
        state.months.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tile -> MonthTileView(tile, onSelectMonth, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthTileView(tile: MonthTile, onSelect: (YearMonth) -> Unit, modifier: Modifier) {
    val shortName = remember { localDateFormat("MMM") }
    val longName = remember { localDateFormat("MMMMyyyy") }
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val total = tile.totalText?.figure()
    val description = if (total != null) {
        stringResource(R.string.expenses_month_tile, tile.month.format(longName), total)
    } else {
        stringResource(R.string.expenses_month_tile_empty, tile.month.format(longName))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(if (total != null) colors.primary.copy(alpha = 0.08f + 0.37f * tile.fraction) else colors.surfaceContainerLow)
            .then(if (tile.isSelected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(enabled = !tile.isFuture) { onSelect(tile.month) }
            .alpha(if (tile.isFuture) 0.38f else 1f)
            // One button per month for screen readers: "August 2026, CHF 2.7K".
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = tile.isSelected
                if (tile.isFuture) disabled() else onClick { onSelect(tile.month); true }
            },
    ) {
        Text(tile.month.format(shortName), style = MaterialTheme.typography.labelMedium)
        Text(
            total ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun YearCalendarPreview() {
    val totals = listOf("3.1K", "2.8K", "4.0K", "2.2K", "3.3K", "2.9K", "5.1K", "2.7K", "1.0K", null, null, null)
    WealthTheme {
        YearCalendar(
            state = YearUiState(
                isLoading = false,
                year = 2026,
                totalText = "CHF 27.1K",
                months = totals.mapIndexed { i, t ->
                    MonthTile(YearMonth.of(2026, i + 1), t?.let { "CHF $it" }, (t?.dropLast(1)?.toFloat() ?: 0f) / 5.1f, isFuture = i > 8, isSelected = i == 7)
                },
                canGoBack = true,
            ),
            onPreviousYear = {}, onNextYear = {}, onSelectMonth = {},
        )
    }
}
