package io.github.codenextdoor.wealth.expenses

import io.github.codenextdoor.wealth.ui.figure
import io.github.codenextdoor.wealth.ui.localDateFormat
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.charts.ChartColors
import io.github.codenextdoor.wealth.ui.charts.DonutWithLegend
import io.github.codenextdoor.wealth.ui.charts.LegendEntry
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ExpensesTab(
    contentPadding: PaddingValues,
    onOpenExpense: (id: Long) -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: ExpensesViewModel = viewModel(factory = ExpensesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val yearState by viewModel.yearState.collectAsStateWithLifecycle()
    ExpensesContent(
        state = state,
        yearState = yearState,
        onPreviousYear = viewModel::previousYear,
        onNextYear = viewModel::nextYear,
        onSelectMonth = viewModel::showMonth,
        contentPadding = contentPadding,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onToggleFilter = viewModel::toggleFilter,
        onOpenExpense = onOpenExpense,
        onOpenRecurring = onOpenRecurring,
    )
}

@Composable
fun ExpensesContent(
    state: ExpensesUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleFilter: (Long?) -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenRecurring: () -> Unit = {},
    yearState: YearUiState = YearUiState(),
    onPreviousYear: () -> Unit = {},
    onNextYear: () -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
) {
    if (state.isLoading) return
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val uncategorized = stringResource(R.string.expenses_uncategorized)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp, // Clear of the add button.
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { YearCalendar(yearState, onPreviousYear, onNextYear, onSelectMonth) }
        item { MonthHeader(state, onPreviousMonth, onNextMonth) }
        item {
            OutlinedButton(onClick = onOpenRecurring, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recurring_title))
            }
        }

        if (!state.hasExpenses) {
            item {
                Text(
                    stringResource(R.string.expenses_empty_month),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            return@LazyColumn
        }

        if (state.slices.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.expenses_by_category),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            stringResource(R.string.expenses_tap_to_filter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        val other = stringResource(R.string.dashboard_other)
                        // Only real categories (and "uncategorized") can filter; "Other" is a mix.
                        val selectedIndex = state.slices.indexOfFirst { it.key != null && it.key == state.filter }.takeIf { it >= 0 }
                        DonutWithLegend(
                            entries = state.slices.map { slice ->
                                LegendEntry(
                                    label = when {
                                        slice.key == null -> other
                                        slice.key == UNCATEGORIZED -> uncategorized
                                        else -> slice.label.orEmpty()
                                    },
                                    amountText = slice.amountText.figure(),
                                    percentText = slice.percentText.figure(),
                                    fraction = slice.fraction,
                                    color = if (slice.colorSlot < 0) ChartColors.other else ChartColors.series(slice.colorSlot),
                                )
                            },
                            centerLabel = stringResource(R.string.expenses_spent),
                            centerValue = state.totalText.figure(),
                            selected = selectedIndex,
                            onSelect = { index -> state.slices[index].key?.let(onToggleFilter) },
                        )
                    }
                }
            }
        }

        if (state.notCounted.isNotEmpty()) {
            item(key = "not-counted") { NotCountedCard(state, onOpenExpense) }
        }

        state.days.forEach { (date, rows) ->
            item(key = date.toString()) {
                Column {
                    Text(
                        date.format(dayFormat),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rows.forEach { row -> ExpenseRowItem(row, onOpenExpense) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(state: ExpensesUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val monthFormat = remember { localDateFormat("MMMMyyyy") }
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.expenses_previous_month))
            }
            Text(
                state.month.format(monthFormat),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = state.canGoForward) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.expenses_next_month))
            }
        }
        if (state.hasExpenses) {
            Text(
                state.totalText.figure(),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            state.vsPreviousText?.let {
                Text(
                    stringResource(
                        if (state.spentMore) R.string.expenses_more_than_previous else R.string.expenses_less_than_previous,
                        it.figure(),
                        state.month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM")),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.excludedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.expenses_excluded, state.excludedCount, state.excludedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Expenses in categories that aren't spending (e.g. transfers to a broker):
 * one line with their total that opens to list them, so they can be fixed.
 */
@Composable
private fun NotCountedCard(state: ExpensesUiState, onOpenExpense: (Long) -> Unit) {
    // Closed again on another month.
    var expanded by rememberSaveable(state.month) { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val count = state.notCounted.size
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.expenses_not_counted_title)) },
            supportingContent = {
                Text(
                    pluralStringResource(
                        R.plurals.expenses_not_counted_summary,
                        count,
                        state.notCountedTotalText.orEmpty().figure(),
                        count.toString().figure(),
                    ),
                )
            },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(if (expanded) R.string.expenses_not_counted_hide else R.string.expenses_not_counted_show),
                    modifier = Modifier.rotate(if (expanded) -90f else 90f),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            state.notCounted.forEach { (date, row) ->
                ExpenseRowItem(row, onOpenExpense, prefix = date.format(dateFormat))
            }
        }
    }
}

/** One expense: description, category (and [prefix], e.g. its date) · account, amount. */
@Composable
private fun ExpenseRowItem(row: ExpenseRow, onOpenExpense: (Long) -> Unit, prefix: String? = null) {
    val uncategorized = stringResource(R.string.expenses_uncategorized)
    ListItem(
        headlineContent = { Text(row.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(prefix, row.categoryName ?: uncategorized, row.accountName).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (row.categoryName == null) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (row.isRefund) stringResource(R.string.expenses_refund, row.amountText.figure()) else row.amountText.figure(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.baseAmountText?.let {
                    Text(
                        stringResource(R.string.account_converted, it.figure()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpenExpense(row.id) },
    )
}
