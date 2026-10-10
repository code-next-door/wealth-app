package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.figure
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import io.github.codenextdoor.wealth.ui.charts.Bar
import io.github.codenextdoor.wealth.ui.charts.BarChart

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    text: String? = null,
    categoryId: Long? = null,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(text, categoryId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        viewModel.query,
        state,
        onBack,
        onOpenExpense,
        SearchActions(viewModel::setCategory, viewModel::setAccount, viewModel::selectMonth),
        // Opened for a shop or a category: the results are what's wanted, not the keyboard.
        focusField = text == null && categoryId == null,
    )
}

/** Filters and the chart's months. */
data class SearchActions(
    val onCategory: (Long?) -> Unit = {},
    val onAccount: (Long?) -> Unit = {},
    val onMonth: (YearMonth) -> Unit = {},
)

/** A search box over every transaction; results open like on the Spending tab. */
@Composable
fun SearchScreen(
    query: TextFieldState,
    state: SearchUiState,
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    actions: SearchActions = SearchActions(),
    focusField: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Ready to type straight away, once: not again when coming back from a result or after
    // the screen is recreated (a keyboard reopening then raced with Back in tests).
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            if (focusField) focus.requestFocus()
        }
    }
    val dateFormat = remember { localDateFormat("dMMMyyyy") }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.search_title), onBack) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                OutlinedTextField(
                    state = query,
                    label = { Text(stringResource(R.string.search_field)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (query.text.isNotEmpty()) {
                            IconButton(onClick = { query.clearText() }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                            }
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    // The keyboard's Search key closes it, so all results are in view.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                )
            }
            item { Filters(state, actions) }
            state.chart?.takeIf { state.count > 0 || state.month != null }?.let { chart -> item { MonthChart(chart, state.month, actions.onMonth) } }
            item { Summary(state) }
            items(state.results, key = { it.row.id }) { result ->
                ExpenseRowItem(result.row, onOpenExpense, prefix = result.date.format(dateFormat), signed = true)
            }
        }
    }
}

@Composable
private fun Filters(state: SearchUiState, actions: SearchActions) {
    val uncategorized = stringResource(R.string.expenses_uncategorized)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        FilterMenu(
            label = stringResource(R.string.search_category),
            anyLabel = stringResource(R.string.search_any_category),
            options = listOf(FilterOption(UNCATEGORIZED, uncategorized)) + state.categories,
            selected = state.categoryFilter,
            onSelect = actions.onCategory,
        )
        FilterMenu(
            label = stringResource(R.string.search_account),
            anyLabel = stringResource(R.string.search_any_account),
            options = state.accounts,
            selected = state.accountFilter,
            onSelect = actions.onAccount,
        )
        state.month?.let { month ->
            val name = remember(month) { month.format(localDateFormat("MMMMyyyy")) }
            val remove = stringResource(R.string.search_remove_filter, name)
            InputChip(
                selected = true,
                onClick = { actions.onMonth(month) },
                label = { Text(stringResource(R.string.search_month_filter, name)) },
                trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = remove, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** A chip that opens a menu of [options]; it shows the chosen one's name. */
@Composable
private fun FilterMenu(label: String, anyLabel: String, options: List<FilterOption>, selected: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = options.firstOrNull { it.id == selected }
    Box {
        FilterChip(
            selected = chosen != null,
            onClick = { open = true },
            label = { Text(chosen?.name ?: label) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(anyLabel) }, onClick = { open = false; onSelect(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = { open = false; onSelect(option.id) })
            }
        }
    }
}

@Composable
private fun MonthChart(chart: SearchChart, selected: YearMonth?, onMonth: (YearMonth) -> Unit) {
    val letter = remember { DateTimeFormatter.ofPattern("MMMMM") }
    val long = remember { localDateFormat("MMMMyyyy") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(if (chart.moneyIn) R.string.search_chart_in else R.string.search_chart_out),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.search_chart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val bars = chart.bars.map { bar ->
            Bar(
                label = bar.month.format(letter),
                description = stringResource(R.string.search_bar, bar.month.format(long), bar.amountText.figure()),
                fraction = bar.fraction,
            )
        }
        BarChart(
            description = stringResource(if (chart.moneyIn) R.string.search_chart_in else R.string.search_chart_out),
            bars = bars,
            selected = chart.bars.indexOfFirst { it.month == selected }.takeIf { it >= 0 },
            onSelect = { onMonth(chart.bars[it].month) },
        )
    }
}

@Composable
private fun Summary(state: SearchUiState) {
    val textModifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    when {
        state.isLoading -> Unit
        !state.searched -> Text(
            stringResource(R.string.search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = textModifier,
        )
        state.count == 0 -> Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodyLarge, modifier = textModifier)
        else -> Column(textModifier) {
            Text(pluralStringResource(R.plurals.search_count, state.count, state.count), style = MaterialTheme.typography.titleMedium)
            state.totals.forEach { total ->
                Text(
                    listOfNotNull(
                        stringResource(R.string.search_total_out, total.outText.figure()),
                        total.inText?.let { stringResource(R.string.search_total_in, it.figure()) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.count > state.results.size) {
                Text(
                    stringResource(R.string.search_showing_newest, state.results.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    WealthTheme {
        SearchScreen(
            query = rememberTextFieldState("coop"),
            state = SearchUiState(
                isLoading = false,
                searched = true,
                count = 2,
                results = listOf(
                    SearchResult(LocalDate.of(2026, 9, 3), ExpenseRow(1, "COOP ZURICH", "Groceries", "Everyday card", "CHF 45.30", null, moneyIn = false)),
                    SearchResult(LocalDate.of(2026, 8, 12), ExpenseRow(2, "COOP REFUND", "Groceries", null, "CHF 4.00", null, moneyIn = true)),
                ),
                totals = listOf(SearchTotal("CHF 45.30", "CHF 4.00")),
            ),
            onBack = {},
            onOpenExpense = {},
        )
    }
}
