package io.github.codenextdoor.wealth.review

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.dashboard.Delta
import io.github.codenextdoor.wealth.dashboard.DeltaText
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.figure
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.time.YearMonth

@Composable
fun MonthlyReviewRoute(month: YearMonth?, onBack: () -> Unit, onOpenAccount: (Long) -> Unit) {
    val viewModel: MonthlyReviewViewModel = viewModel(factory = MonthlyReviewViewModel.factory(month))
    val state by viewModel.state.collectAsStateWithLifecycle()
    MonthlyReviewScreen(state, onBack, viewModel::previous, viewModel::next, onOpenAccount)
}

/** A finished month: net worth, income and spending, top categories, accounts to update. */
@Composable
fun MonthlyReviewScreen(
    state: MonthlyReviewUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenAccount: (Long) -> Unit,
) {
    val monthFormat = remember { localDateFormat("MMMMyyyy") }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.review_title), onBack) }) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, enabled = state.canGoBack) {
                        Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.expenses_previous_month))
                    }
                    Text(
                        state.month.atDay(1).format(monthFormat),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = onNext, enabled = state.canGoForward) {
                        Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.expenses_next_month))
                    }
                }
            }
            if (state.change != null) {
                item {
                    ReviewCard(stringResource(R.string.review_net_worth)) {
                        Line(stringResource(R.string.review_start), state.startText.figure())
                        Line(stringResource(R.string.review_end), state.endText.figure())
                        DeltaText(state.change, strong = true)
                        if (state.movers.isNotEmpty()) {
                            Text(stringResource(R.string.review_movers), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            state.movers.forEach { mover ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenAccount(mover.accountId) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(mover.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    DeltaText(mover.change)
                                }
                            }
                        }
                    }
                }
            }
            item {
                ReviewCard(stringResource(R.string.review_cash_flow)) {
                    Line(stringResource(R.string.review_income), state.incomeText.figure())
                    Line(stringResource(R.string.review_spending), state.spendingText.figure())
                    state.spendingChange?.let { DeltaText(it, suffix = stringResource(R.string.review_vs_month_before)) }
                    Line(
                        stringResource(R.string.review_saved),
                        listOfNotNull(state.savedText.figure(), state.savingsRateText?.let { "(${it.figure()})" }).joinToString(" "),
                    )
                    state.previousSavingsRateText?.let {
                        Text(
                            stringResource(R.string.review_saved_before, it.figure()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.categories.isNotEmpty()) {
                item {
                    ReviewCard(stringResource(R.string.review_top_categories)) {
                        state.categories.forEach { category ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Line(category.name ?: stringResource(R.string.expenses_uncategorized), category.amountText.figure())
                                DeltaText(category.change, suffix = stringResource(R.string.review_vs_month_before))
                            }
                        }
                    }
                }
            }
            if (state.notUpdated.isNotEmpty()) {
                item {
                    ReviewCard(stringResource(R.string.review_not_updated)) {
                        Text(
                            stringResource(R.string.review_not_updated_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.notUpdated.forEach { (id, name) ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAccount(id) }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

/** Label on the left, value on the right; each wraps in its half. */
@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlyReviewPreview() {
    WealthTheme {
        MonthlyReviewScreen(
            state = MonthlyReviewUiState(
                isLoading = false,
                month = YearMonth.of(2026, 9),
                canGoBack = true,
                hasAccounts = true,
                startText = "CHF 120,000.00",
                endText = "CHF 122,340.00",
                change = Delta("+CHF 2,340.00", "+1.9%", isIncrease = true),
                movers = listOf(ReviewMover(1, "Savings", Delta("+CHF 1,800.00", null, true))),
                incomeText = "CHF 9,000.00",
                spendingText = "CHF 4,200.00",
                savedText = "CHF 4,800.00",
                savingsRateText = "53.3%",
                spendingChange = Delta("−CHF 300.00", "−6.7%", false),
                previousSavingsRateText = "48.0%",
                categories = listOf(ReviewCategory("Rent", "CHF 2,000.00", "CHF 2,000.00", Delta("+CHF 0.00", "+0.0%", true))),
                notUpdated = listOf(9L to "Pillar 3a"),
            ),
            onBack = {},
            onPrevious = {},
            onNext = {},
            onOpenAccount = {},
        )
    }
}
