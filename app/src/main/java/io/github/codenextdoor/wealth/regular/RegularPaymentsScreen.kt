package io.github.codenextdoor.wealth.regular

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.RegularPayments
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.figure
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.time.LocalDate

@Composable
fun RegularPaymentsRoute(
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
    viewModel: RegularPaymentsViewModel = viewModel(factory = RegularPaymentsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegularPaymentsScreen(state, onBack, onOpenShop, onHide = viewModel::hide, onShow = viewModel::show)
}

/** Every regular payment found: active ones with their yearly cost, then stopped and hidden ones. */
@Composable
fun RegularPaymentsScreen(
    state: RegularPaymentsUiState,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
    onHide: (String) -> Unit,
    onShow: (String) -> Unit,
) {
    var showStopped by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.regular_title), onBack) }) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(contentPadding = padding) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (state.active.isNotEmpty()) {
                        Text(
                            stringResource(R.string.regular_total, state.yearlyTotalText.figure(), state.monthlyTotalText.figure()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        stringResource(if (state.active.isEmpty()) R.string.regular_empty else R.string.regular_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(state.active, key = { "a-" + it.shop + it.amountText }) { row ->
                PaymentRow(row, active = true, onOpen = onOpenShop, action = {
                    IconButton(onClick = { onHide(row.shop) }) {
                        Icon(painterResource(R.drawable.ic_visibility_off), stringResource(R.string.regular_hide))
                    }
                })
            }
            if (state.stopped.isNotEmpty()) {
                item { SectionToggle(stringResource(R.string.regular_stopped, state.stopped.size), showStopped) { showStopped = !showStopped } }
                if (showStopped) {
                    item {
                        Text(
                            stringResource(R.string.regular_stopped_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(state.stopped, key = { "s-" + it.shop + it.amountText }) { row ->
                        PaymentRow(row, active = false, onOpen = onOpenShop, action = {
                            IconButton(onClick = { onHide(row.shop) }) {
                                Icon(painterResource(R.drawable.ic_visibility_off), stringResource(R.string.regular_hide))
                            }
                        })
                    }
                }
            }
            if (state.hidden.isNotEmpty()) {
                item { SectionToggle(stringResource(R.string.regular_hidden, state.hidden.size), showHidden) { showHidden = !showHidden } }
                if (showHidden) {
                    items(state.hidden, key = { "h-" + it.shop + it.amountText }) { row ->
                        PaymentRow(row, active = false, onOpen = onOpenShop, action = {
                            IconButton(onClick = { onShow(row.shop) }) {
                                Icon(painterResource(R.drawable.ic_visibility), stringResource(R.string.regular_show_again))
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(row: RegularPaymentRow, active: Boolean, onOpen: (String) -> Unit, action: @Composable () -> Unit) {
    val nextFormat = remember { localDateFormat("dMMM") }
    val lastFormat = remember { localDateFormat("dMMMyyyy") }
    val charge = stringResource(
        if (row.cadence == RegularPayments.Cadence.MONTHLY) R.string.regular_monthly else R.string.regular_yearly,
        row.amountText.figure(),
    )
    ListItem(
        headlineContent = { Text(row.shop) },
        supportingContent = {
            Text(
                if (active) stringResource(R.string.regular_next, charge, row.nextDate.format(nextFormat))
                else stringResource(R.string.regular_last, charge, row.lastDate.format(lastFormat)),
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                row.yearlyText?.let {
                    Text(stringResource(R.string.regular_a_year, it.figure()), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                }
                action()
            }
        },
        modifier = Modifier.clickable { onOpen(row.shop) },
    )
}

@Composable
private fun SectionToggle(label: String, open: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp).rotate(if (open) 90f else 0f),
        )
        Text(label)
    }
}

/** The Spending tab's card: how many there are and a year of them; opens the list. */
@Composable
fun RegularPaymentsCard(state: RegularPaymentsUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (state.isLoading || state.active.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.regular_title)) },
            supportingContent = {
                Text(stringResource(R.string.regular_card_summary, state.active.size.toString().figure(), state.yearlyTotalText.figure()))
            },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegularPaymentsScreenPreview() {
    WealthTheme {
        RegularPaymentsScreen(
            state = RegularPaymentsUiState(
                isLoading = false,
                active = listOf(
                    RegularPaymentRow("NETFLIX", "CHF 17.90", RegularPayments.Cadence.MONTHLY, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 3), "CHF 214.80"),
                    RegularPaymentRow("AMAZON", "CHF 99.00", RegularPayments.Cadence.YEARLY, LocalDate.of(2026, 3, 14), LocalDate.of(2027, 3, 14), "CHF 99.00"),
                ),
                stopped = listOf(RegularPaymentRow("SPOTIFY", "CHF 12.95", RegularPayments.Cadence.MONTHLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "CHF 155.40")),
                yearlyTotalText = "CHF 313.80",
                monthlyTotalText = "CHF 26.15",
            ),
            onBack = {},
            onOpenShop = {},
            onHide = {},
            onShow = {},
        )
    }
}
