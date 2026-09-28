package io.github.codenextdoor.wealth.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.ui.charts.ChartColors
import io.github.codenextdoor.wealth.ui.charts.ChartPoint
import io.github.codenextdoor.wealth.ui.charts.DonutWithLegend
import io.github.codenextdoor.wealth.ui.charts.LegendEntry
import io.github.codenextdoor.wealth.ui.charts.LineChart
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import io.github.codenextdoor.wealth.ui.theme.heroBrush
import io.github.codenextdoor.wealth.ui.theme.onHeroColor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DashboardTab(
    contentPadding: PaddingValues,
    onAddAccount: () -> Unit,
    onOpenAccount: (id: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBackfill: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        contentPadding = contentPadding,
        onAddAccount = onAddAccount,
        onOpenAccount = onOpenAccount,
        onOpenHistory = onOpenHistory,
        onOpenBackfill = onOpenBackfill,
        onRangeChange = viewModel::selectRange,
        onBreakdownChange = viewModel::selectBreakdown,
        onPeriodChange = viewModel::selectPeriod,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    contentPadding: PaddingValues,
    onAddAccount: () -> Unit,
    onOpenAccount: (id: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBackfill: () -> Unit = {},
    onRangeChange: (ChartRange) -> Unit,
    onBreakdownChange: (BreakdownBy) -> Unit,
    onPeriodChange: (ChangePeriod) -> Unit,
) {
    if (state.isLoading) return
    if (!state.hasAccounts) {
        EmptyDashboard(contentPadding, onAddAccount)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NetWorthCard(state) }
        item { HistoryCard(state, onRangeChange, onOpenHistory, onOpenBackfill) }
        item { BreakdownCard(state, onBreakdownChange) }
        item { ChangesCard(state, onPeriodChange, onOpenAccount) }
    }
}

@Composable
private fun EmptyDashboard(contentPadding: PaddingValues, onAddAccount: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.dashboard_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddAccount, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.account_add))
        }
    }
}

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun <T> Selector(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/** Arrow + sign + amount. Never color alone: the arrow and sign carry the meaning. */
@Composable
private fun DeltaText(delta: Delta, suffix: String? = null, strong: Boolean = false) {
    val color = if (delta.isIncrease) ChartColors.increase() else ChartColors.decrease()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (delta.isIncrease) painterResource(R.drawable.ic_arrow_upward) else painterResource(R.drawable.ic_arrow_downward),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        val text = listOfNotNull(delta.amountText, delta.percentText?.let { "($it)" }).joinToString(" ")
        Text(
            text,
            color = color,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
        if (suffix != null) {
            Text(
                " $suffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- 1. Headline -----------------------------------------------------------

@Composable
private fun NetWorthCard(state: DashboardUiState) {
    val onHero = onHeroColor()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(heroBrush())
            .padding(20.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.dashboard_net_worth),
                style = MaterialTheme.typography.labelLarge,
                color = onHero.copy(alpha = 0.8f),
            )
            Text(
                state.netWorthText,
                style = MaterialTheme.typography.displaySmall,
                color = onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.recentChange?.let { change ->
                val since = state.recentChangeSince
                val suffix = if (since == null) {
                    stringResource(R.string.dashboard_change_last_month)
                } else {
                    stringResource(R.string.dashboard_change_since, since.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                }
                // On the colored card the arrow and sign carry the direction, in the card's text color.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(onHero.copy(alpha = 0.14f), CircleShape)
                        .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Icon(
                        if (change.isIncrease) painterResource(R.drawable.ic_arrow_upward) else painterResource(R.drawable.ic_arrow_downward),
                        contentDescription = null,
                        tint = onHero,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        listOfNotNull(change.amountText, change.percentText?.let { "($it)" }).joinToString(" ") + " " + suffix,
                        style = MaterialTheme.typography.labelLarge,
                        color = onHero,
                    )
                }
            }
            Row(Modifier.padding(top = 20.dp)) {
                Stat(stringResource(R.string.dashboard_assets), state.assetsText, onHero, Modifier.weight(1f))
                Stat(stringResource(R.string.dashboard_liabilities), state.liabilitiesText, onHero, Modifier.weight(1f))
            }
            state.housesOutsideText?.let {
                Text(
                    stringResource(R.string.dashboard_houses_outside, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            state.unvestedText?.let {
                Text(
                    stringResource(R.string.dashboard_unvested, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (state.excludedCount > 0) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                        .padding(12.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.dashboard_excluded,
                            state.excludedCount,
                            state.excludedCount,
                            state.missingRateCurrencies.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- 2. Net worth over time -----------------------------------------------

@Composable
private fun HistoryCard(state: DashboardUiState, onRangeChange: (ChartRange) -> Unit, onOpenHistory: () -> Unit, onOpenBackfill: () -> Unit) {
    DashboardCard {
        CardTitle(stringResource(R.string.dashboard_history_title))
        if (state.history.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenBackfill, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.dashboard_history_backfill))
            }
            return@DashboardCard
        }
        Selector(
            options = ChartRange.entries,
            selected = state.range,
            label = {
                stringResource(
                    when (it) {
                        ChartRange.SIX_MONTHS -> R.string.range_6m
                        ChartRange.ONE_YEAR -> R.string.range_1y
                        ChartRange.ALL -> R.string.range_all
                    },
                )
            },
            onSelect = onRangeChange,
        )
        val axisFormat = { v: Float -> formatMoney(BigDecimal(v.toDouble()), state.baseCurrency, 0) }
        val dateFormat = DateTimeFormatter.ofPattern("MMM yyyy")
        LineChart(
            history = state.history,
            forecast = state.forecast,
            formatAxisValue = axisFormat,
            formatDate = { it.format(dateFormat) },
            projectedLabel = stringResource(R.string.dashboard_projected),
            contentDescription = stringResource(R.string.dashboard_history_chart_description, state.netWorthText),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        if (state.forecast.isNotEmpty()) {
            ChartLegend()
        }

        Spacer(Modifier.height(12.dp))
        val trend = state.trendPerMonth
        if (trend == null) {
            Text(
                stringResource(R.string.dashboard_trend_needs_history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dashboard_trend), style = MaterialTheme.typography.bodyMedium)
                DeltaText(trend, suffix = stringResource(R.string.dashboard_per_month))
            }
            state.projectionText?.let {
                Text(
                    pluralStringResource(R.plurals.dashboard_projection, state.projectionMonths.toInt(), state.projectionMonths.toInt(), it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            stringResource(R.string.dashboard_history_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onOpenHistory, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.dashboard_see_history))
        }
    }
}

/** Two series (actual and projected) need a legend; the line style is the key. */
@Composable
private fun ChartLegend() {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendLine(color, dashed = false)
        Text(stringResource(R.string.dashboard_actual), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, end = 16.dp))
        LegendLine(color, dashed = true)
        Text(stringResource(R.string.dashboard_projected), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
        drawLine(
            color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

// ---- 3. Where your money is ------------------------------------------------

@Composable
private fun BreakdownCard(state: DashboardUiState, onBreakdownChange: (BreakdownBy) -> Unit) {
    DashboardCard {
        CardTitle(stringResource(R.string.dashboard_breakdown_title))
        Selector(
            options = BreakdownBy.entries,
            selected = state.breakdownBy,
            label = {
                stringResource(
                    when (it) {
                        BreakdownBy.TYPE -> R.string.breakdown_type
                        BreakdownBy.COUNTRY -> R.string.breakdown_country
                        BreakdownBy.CURRENCY -> R.string.breakdown_currency
                    },
                )
            },
            onSelect = onBreakdownChange,
        )
        if (state.slices.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_breakdown_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DashboardCard
        }
        val general = stringResource(R.string.account_types_section_general)
        val other = stringResource(R.string.dashboard_other)
        DonutWithLegend(
            entries = state.slices.map { slice ->
                LegendEntry(
                    label = slice.label ?: if (slice.isOther) other else general,
                    amountText = slice.amountText,
                    percentText = slice.percentText,
                    fraction = slice.fraction,
                    color = if (slice.isOther) ChartColors.other else ChartColors.series(slice.colorSlot),
                )
            },
            centerLabel = stringResource(R.string.dashboard_assets),
            centerValue = state.assetsTotalText,
        )
    }
}

// ---- 4. What changed -------------------------------------------------------

@Composable
private fun ChangesCard(state: DashboardUiState, onPeriodChange: (ChangePeriod) -> Unit, onOpenAccount: (Long) -> Unit) {
    DashboardCard {
        CardTitle(stringResource(R.string.dashboard_changes_title))
        Selector(
            options = ChangePeriod.entries,
            selected = state.changePeriod,
            label = { stringResource(if (it == ChangePeriod.MONTH) R.string.period_month else R.string.period_year) },
            onSelect = onPeriodChange,
        )
        state.periodChange?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.dashboard_net_worth), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                DeltaText(it, strong = true)
            }
        }
        if (state.movers.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_changes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DashboardCard
        }
        state.movers.forEach { mover ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAccount(mover.accountId) }
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(mover.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (mover.detail.isNotEmpty()) {
                        Text(
                            mover.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DeltaText(mover.change)
            }
        }
        if (state.moreMovers > 0) {
            Text(
                pluralStringResource(R.plurals.dashboard_more_changes, state.moreMovers, state.moreMovers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun DashboardPreview() {
    val start = LocalDate.of(2025, 10, 1)
    val history = (0..12).map { ChartPoint(start.plusMonths(it.toLong()), 100_000f + it * 2_500f + (it % 3) * 900f, "CHF ${100 + it * 2},500") }
    WealthTheme {
        DashboardContent(
            state = DashboardUiState(
                isLoading = false,
                hasAccounts = true,
                netWorthText = "CHF 132,450.00",
                assetsText = "CHF 140,000.00",
                liabilitiesText = "CHF 7,550.00",
                recentChange = Delta("+CHF 2,340.00", "+1.8%", true),
                history = history,
                forecast = (1..12).map { ChartPoint(history.last().date.plusMonths(it.toLong()), 132_450f + it * 2_400f, "~CHF 150,000") },
                trendPerMonth = Delta("+CHF 2,400", null, true),
                projectionText = "~CHF 161,250",
                slices = listOf(
                    Slice("Bank account", false, "CHF 60,000.00", "42.9%", 0.429f, 0),
                    Slice("Pillar 3a", false, "CHF 35,000.00", "25.0%", 0.25f, 1),
                    Slice("NRE account", false, "CHF 25,000.00", "17.9%", 0.179f, 2),
                    Slice(null, true, "CHF 20,000.00", "14.3%", 0.143f, -1),
                ),
                assetsTotalText = "CHF 140,000.00",
                periodChange = Delta("+CHF 2,340.00", "+1.8%", true),
                movers = listOf(
                    Mover(1, "Salary account", "Bank account · Switzerland", Delta("+CHF 3,100.00", null, true)),
                    Mover(2, "Credit card", "Credit card", Delta("−CHF 760.00", null, false)),
                ),
                baseCurrency = "CHF",
            ),
            contentPadding = PaddingValues(),
            onAddAccount = {}, onOpenAccount = {}, onOpenHistory = {}, onRangeChange = {}, onBreakdownChange = {}, onPeriodChange = {},
        )
    }
}
