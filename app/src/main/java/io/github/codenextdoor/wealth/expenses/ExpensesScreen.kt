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
    val notCounted = stringResource(R.string.expenses_not_counted)

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
                        rows.forEach { row ->
                            // Not counted (e.g. money moved to a broker): greyed, and says so.
                            val textColor = if (row.isCounted) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                            ListItem(
                                headlineContent = { Text(row.description, maxLines = 1, overflow = TextOverflow.Ellipsis, color = textColor) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            row.categoryName ?: uncategorized,
                                            row.accountName,
                                            if (row.isCounted) null else notCounted,
                                        ).joinToString(" · "),
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
                                            color = if (row.isCounted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
