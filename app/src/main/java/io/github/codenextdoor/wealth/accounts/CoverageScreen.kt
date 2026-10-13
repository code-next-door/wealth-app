package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.Coverage.Mark
import io.github.codenextdoor.wealth.ui.components.AccountBadge
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale

@Composable
fun CoverageRoute(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onBackfill: () -> Unit,
    viewModel: CoverageViewModel = viewModel(factory = CoverageViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CoverageScreen(
        state = state,
        onBack = onBack,
        onSelectYear = viewModel::selectYear,
        onAddBalance = viewModel::addBalance,
        onImport = onImport,
        onBackfill = onBackfill,
        rateLookups = viewModel.rateLookups,
        priceLookups = viewModel.priceLookups,
    )
}

/** Each account's months with and without a statement, a year at a time; tap an account to fill a gap. */
@Composable
fun CoverageScreen(
    state: CoverageUiState,
    onBack: () -> Unit,
    onSelectYear: (Int) -> Unit,
    onAddBalance: (accountId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal?, price: PriceEntry?) -> Unit,
    onImport: () -> Unit,
    onBackfill: () -> Unit,
    /** Download rates and prices; null in previews. */
    rateLookups: RateLookups? = null,
    priceLookups: RateLookups? = null,
) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    // "accountId|yyyy-MM" of the balance being added.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.coverage_title), onBack) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Text(
                    stringResource(R.string.coverage_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (state.isLoading) return@LazyColumn
            if (state.years.isEmpty()) {
                item { Text(stringResource(R.string.coverage_nothing_yet), style = MaterialTheme.typography.bodyLarge) }
                return@LazyColumn
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelectYear(state.year - 1) }, enabled = state.year > state.years.first) {
                        Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.expenses_previous_year))
                    }
                    Text(state.year.toString(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onSelectYear(state.year + 1) }, enabled = state.year < state.years.last) {
                        Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.expenses_next_year))
                    }
                }
            }
            item { Legend() }
            if (state.rows.isEmpty()) {
                item { Text(stringResource(R.string.coverage_nothing_this_year), style = MaterialTheme.typography.bodyLarge) }
            }
            items(state.rows, key = { it.accountId }) { row -> CoverageCard(row, onOpen = { openId = row.accountId }) }
        }
    }

    openId?.let { id ->
        val row = state.rows.firstOrNull { it.accountId == id }
        if (row == null) {
            openId = null
        } else {
            MissingDialog(
                row = row,
                onAddBalance = { month -> openId = null; adding = "$id|$month" },
                onImport = { openId = null; onImport() },
                onBackfill = { openId = null; onBackfill() },
                onDismiss = { openId = null },
            )
        }
    }

    adding?.let { key ->
        val (id, month) = key.split("|").let { it[0].toLong() to YearMonth.parse(it[1]) }
        val row = state.rows.firstOrNull { it.accountId == id }
        if (row == null) {
            adding = null
        } else {
            BalanceEntryDialog(
                title = stringResource(R.string.balance_entry_add),
                accountName = row.name,
                currencyCode = row.currencyCode,
                decimals = row.decimals,
                initialAmountText = "",
                initialDate = month.atEndOfMonth(),
                isLiability = row.isLiability,
                rates = RateSupport(row.currencyCode, state.baseCurrency, state.rateBook, rateLookups),
                onSave = { date, minor, rate, units, price -> onAddBalance(id, date, minor, rate, units, price); adding = null },
                onDelete = null,
                onDismiss = { adding = null },
                shares = row.shareSymbol?.let { SharesSupport(it, row.currencyCode, state.priceBook, priceLookups) },
            )
        }
    }
}

@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        listOf(Mark.DONE to R.string.coverage_legend_done, Mark.CALCULATED to R.string.coverage_legend_calculated, Mark.MISSING to R.string.coverage_legend_missing)
            .forEach { (mark, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkDot(mark)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                }
            }
    }
}

/** Tapped as a whole (12 months don't fit 48 dp targets); read out as its name and what's missing. */
@Composable
private fun CoverageCard(row: CoverageRow, onOpen: () -> Unit) {
    val shortMonth = remember { localDateFormat("MMM") }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountBadge(row.name, row.isLiability)
                Text(row.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
            }
            MonthLine(label = "") { month ->
                Text(Month.of(month).getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale), style = MaterialTheme.typography.labelSmall)
            }
            row.balances?.let { marks -> MonthLine(stringResource(R.string.coverage_balances)) { MarkDot(marks[it - 1]) } }
            row.transactions?.let { marks -> MonthLine(stringResource(R.string.coverage_transactions)) { MarkDot(marks[it - 1]) } }
            Text(
                if (row.missing.isEmpty()) {
                    stringResource(R.string.coverage_complete)
                } else {
                    stringResource(R.string.coverage_missing, row.missing.joinToString { it.format(shortMonth) })
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.missing.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A label and twelve month cells; the marks are read out through the card's text instead. */
@Composable
private fun MonthLine(label: String, cell: @Composable (month: Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(3f))
        Row(Modifier.weight(7f)) {
            (1..12).forEach { month ->
                Box(Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.Center) { cell(month) }
            }
        }
    }
}

/** Told apart by shape, not only colour: filled, half filled, an empty ring; nothing when not expected. */
@Composable
private fun MarkDot(mark: Mark?) {
    val color = MaterialTheme.colorScheme.primary
    val missing = MaterialTheme.colorScheme.error
    Canvas(Modifier.size(10.dp)) {
        val ring = Stroke(1.5.dp.toPx())
        when (mark) {
            Mark.DONE -> drawCircle(color)
            Mark.CALCULATED -> {
                drawCircle(color, style = ring)
                drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
            }
            Mark.MISSING -> drawCircle(missing, radius = size.minDimension / 2 - ring.width / 2, style = ring)
            null -> Unit
        }
    }
}

@Composable
private fun MissingDialog(
    row: CoverageRow,
    onAddBalance: (YearMonth) -> Unit,
    onImport: () -> Unit,
    onBackfill: () -> Unit,
    onDismiss: () -> Unit,
) {
    val longMonth = remember { localDateFormat("MMMMyyyy") }
    val noTransactions = row.missing.filter { month -> row.transactions?.get(month.monthValue - 1) == Mark.MISSING }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (row.missing.isEmpty()) Text(stringResource(R.string.coverage_complete))
                if (row.missingBalances.isNotEmpty()) {
                    Text(stringResource(R.string.coverage_missing_balance, row.missingBalances.joinToString { it.format(longMonth) }))
                    row.missingBalances.forEach { month ->
                        TextButton(onClick = { onAddBalance(month) }) { Text(stringResource(R.string.coverage_add_balance, month.format(longMonth))) }
                    }
                    TextButton(onClick = onBackfill) { Text(stringResource(R.string.backfill_title)) }
                }
                if (noTransactions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.coverage_missing_transactions, noTransactions.joinToString { it.format(longMonth) }),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = onImport) { Text(stringResource(R.string.import_title)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun CoverageScreenPreview() {
    val year = List(12) { if (it < 3) null else if (it in 5..6) Mark.MISSING else if (it == 4) Mark.CALCULATED else Mark.DONE }
    WealthTheme {
        CoverageScreen(
            state = CoverageUiState(
                isLoading = false,
                year = 2025,
                years = 2024..2025,
                rows = listOf(
                    CoverageRow(1, "Salary account", false, "CHF", 2, null, year, year.map { it?.let { m -> if (m == Mark.CALCULATED) Mark.DONE else m } }, listOf(YearMonth.of(2025, 6), YearMonth.of(2025, 7)), listOf(YearMonth.of(2025, 6), YearMonth.of(2025, 7))),
                    CoverageRow(2, "Credit card", true, "CHF", 2, null, null, List(12) { Mark.DONE }, emptyList(), emptyList()),
                ),
            ),
            onBack = {},
            onSelectYear = {},
            onAddBalance = { _, _, _, _, _, _ -> },
            onImport = {},
            onBackfill = {},
        )
    }
}
