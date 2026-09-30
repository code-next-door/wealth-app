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
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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

    // Swipe right for the month before, left for the next (up to this month). The
    // month part follows the finger a little, and a new month slides in from its side
    // (also when a tile is tapped). The tiles and year arrows stay the visible way.
    val width = LocalWindowInfo.current.containerSize.width.toFloat().coerceAtLeast(1f)
    val threshold = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    val slide = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var shownMonth by remember { mutableStateOf(state.month) }
    LaunchedEffect(state.month) {
        if (state.month != shownMonth) {
            val from = if (state.month > shownMonth) width / 3 else -width / 3
            shownMonth = state.month
            slide.snapTo(from)
            slide.animateTo(0f, tween(durationMillis = 250))
        }
    }
    val canGoForward by rememberUpdatedState(state.canGoForward)
    val previous by rememberUpdatedState(onPreviousMonth)
    val next by rememberUpdatedState(onNextMonth)
    val swipe = Modifier.pointerInput(Unit) {
        var dragged = 0f
        fun settle() = scope.launch { slide.animateTo(0f) }
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = {
                when {
                    dragged > threshold -> previous()
                    dragged < -threshold && canGoForward -> next()
                    else -> settle()
                }
            },
            onDragCancel = { settle() },
        ) { change, amount ->
            change.consume()
            dragged += amount
            scope.launch { slide.snapTo(dragged * FOLLOW) }
        }
    }
    val monthMotion = Modifier.graphicsLayer {
        translationX = slide.value
        alpha = 1f - (abs(slide.value) / width).coerceIn(0f, 0.6f)
    }
    fun LazyListScope.monthItem(key: Any? = null, content: @Composable () -> Unit) =
        item(key) { Box(monthMotion) { content() } }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp, // Clear of the add button.
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = swipe,
    ) {
        item { YearCalendar(yearState, onPreviousYear, onNextYear, onSelectMonth) }
        monthItem { MonthHeader(state, onPreviousMonth, onNextMonth) }
        monthItem {
            OutlinedButton(onClick = onOpenRecurring, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recurring_title))
            }
        }

        if (!state.hasExpenses) {
            monthItem {
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
            monthItem {
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

        if (state.income.isNotEmpty()) {
            monthItem(key = "income") {
                val count = state.income.size
                RowsCard(
                    title = stringResource(R.string.expenses_income_title),
                    summary = pluralStringResource(
                        R.plurals.expenses_income_summary,
                        count,
                        state.incomeTotalText.orEmpty().figure(),
                        count.toString().figure(),
                    ),
                    rows = state.income,
                    month = state.month,
                    onOpenExpense = onOpenExpense,
                )
            }
        }

        if (state.notCounted.isNotEmpty()) {
            monthItem(key = "not-counted") {
                val count = state.notCounted.size
                RowsCard(
                    title = stringResource(R.string.expenses_not_counted_title),
                    summary = pluralStringResource(
                        R.plurals.expenses_not_counted_summary,
                        count,
                        state.notCountedTotalText.orEmpty().figure(),
                        count.toString().figure(),
                    ),
                    rows = state.notCounted,
                    month = state.month,
                    onOpenExpense = onOpenExpense,
                )
            }
        }

        state.days.forEach { (date, rows) ->
            monthItem(key = date.toString()) {
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
    val previousLabel = stringResource(R.string.expenses_previous_month)
    val nextLabel = stringResource(R.string.expenses_next_month)
    // No arrows (the tiles and swiping change month); screen readers get them as actions.
    Column(
        Modifier
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(previousLabel) { onPrevious(); true },
                    if (state.canGoForward) CustomAccessibilityAction(nextLabel) { onNext(); true } else null,
                )
            },
    ) {
        Text(
            state.month.format(monthFormat),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
            if (state.incomeTotalText != null && state.savedText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    FlowTile(
                        label = stringResource(R.string.expenses_income_title),
                        value = "+" + state.incomeTotalText.figure(),
                        modifier = Modifier.weight(1f),
                    )
                    FlowTile(
                        label = stringResource(R.string.expenses_saved),
                        value = state.savedPercentText?.let {
                            stringResource(R.string.expenses_saved_value, state.savedText.figure(), it.figure())
                        } ?: state.savedText.figure(),
                        modifier = Modifier.weight(1f),
                    )
                }
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
 * Rows kept apart from spending, one card each: income (salary, interest…) and
 * categories that aren't spending (e.g. transfers to a broker). One line with
 * their total that opens to list them, so they can be checked and fixed.
 */
@Composable
private fun RowsCard(
    title: String,
    summary: String,
    rows: List<Pair<java.time.LocalDate, ExpenseRow>>,
    month: YearMonth,
    onOpenExpense: (Long) -> Unit,
) {
    // Closed again on another month.
    var expanded by rememberSaveable(month, title) { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
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
            rows.forEach { (date, row) ->
                ExpenseRowItem(row, onOpenExpense, prefix = date.format(dateFormat), signed = true)
            }
        }
    }
}

/**
 * One expense: description, category (and [prefix], e.g. its date) · account, amount.
 * [signed]: "+"/"−" for which way the money went (income, not counted); otherwise
 * spending shows plain and money in as a refund.
 */
@Composable
private fun ExpenseRowItem(row: ExpenseRow, onOpenExpense: (Long) -> Unit, prefix: String? = null, signed: Boolean = false) {
    val uncategorized = stringResource(if (signed && row.moneyIn) R.string.expenses_uncategorized_income else R.string.expenses_uncategorized)
    val amount = row.amountText.figure()
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
                    when {
                        signed -> (if (row.moneyIn) "+" else "−") + amount
                        row.isRefund -> stringResource(R.string.expenses_refund, amount)
                        else -> amount
                    },
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

/** A small figure under the month total: income, or what was saved. */
@Composable
private fun FlowTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** How far a swipe must go to change month. */
private val SWIPE_DISTANCE = 72.dp

/** How much of the finger's movement the month part follows while swiping. */
private const val FOLLOW = 0.3f
