package io.github.codenextdoor.wealth.accounts

import io.github.codenextdoor.wealth.ui.localDateFormat
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.components.AccountBadge
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onBack = onBack,
        onSelectAccount = viewModel::selectAccount,
        onUpdate = viewModel::updateEntry,
        onDelete = viewModel::deleteEntry,
        rateLookups = viewModel.rateLookups,
        priceLookups = viewModel.priceLookups,
    )
}

/** Every saved balance across all accounts, editable in place. */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onUpdate: (entryId: Long, date: java.time.LocalDate, balanceMinor: Long, rate: RateEntry?, units: java.math.BigDecimal?, price: PriceEntry?) -> Unit,
    onDelete: (entryId: Long) -> Unit,
    /** Downloads rates; null in previews. */
    rateLookups: RateLookups? = null,
    priceLookups: RateLookups? = null,
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val rows = state.months.flatMap { it.second }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val monthFormat = remember { localDateFormat("MMMMyyyy") }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.history_title), onBack) }) { padding ->
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
                    stringResource(R.string.history_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (state.accounts.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        item {
                            FilterChip(
                                selected = state.selectedAccountId == null,
                                onClick = { onSelectAccount(null) },
                                label = { Text(stringResource(R.string.history_all_accounts)) },
                            )
                        }
                        items(state.accounts, key = { it.id }) { account ->
                            FilterChip(
                                selected = state.selectedAccountId == account.id,
                                onClick = { onSelectAccount(account.id) },
                                label = { Text(account.name) },
                            )
                        }
                    }
                }
            }
            state.months.forEach { (month, entries) ->
                item(key = month.toString()) {
                    Text(
                        month.format(monthFormat),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                item(key = "$month-entries") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        entries.forEach { row ->
                            ListItem(
                                // Same badge as the Accounts tab; debts also show a minus sign.
                                leadingContent = { AccountBadge(row.accountName, row.isLiability) },
                                headlineContent = { Text(row.accountName) },
                                supportingContent = { Text(row.date.format(dateFormat)) },
                                trailingContent = {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(row.amountText, style = MaterialTheme.typography.titleSmall)
                                        Icon(
                                            painterResource(R.drawable.ic_edit),
                                            contentDescription = stringResource(R.string.balance_entry_edit),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { editingId = row.entryId },
                            )
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val row = rows.firstOrNull { it.entryId == id }
        if (row == null) {
            editingId = null
        } else {
            BalanceEntryDialog(
                title = stringResource(R.string.balance_entry_edit),
                accountName = row.accountName,
                currencyCode = row.currencyCode,
                decimals = row.decimals,
                initialAmountText = minorToInputText(row.balanceMinor, row.decimals),
                initialDate = row.date,
                isLiability = row.isLiability,
                rates = RateSupport(row.currencyCode, state.baseCurrency, state.rateBook, rateLookups),
                onSave = { date, minor, rate, units, price -> onUpdate(id, date, minor, rate, units, price); editingId = null },
                onDelete = if (row.canDelete) ({ editingId = null; deletingId = id }) else null,
                onDismiss = { editingId = null },
                shares = row.shareSymbol?.let { SharesSupport(it, row.currencyCode, state.priceBook, priceLookups) },
                initialUnitsText = row.units?.stripTrailingZeros()?.toPlainString().orEmpty(),
            )
        }
    }

    deletingId?.let { id ->
        val row = rows.firstOrNull { it.entryId == id }
        ConfirmDeleteDialog(
            itemName = row?.let { "${it.accountName}, ${it.date.format(dateFormat)}" }.orEmpty(),
            message = stringResource(R.string.account_history_delete_message),
            onConfirm = { onDelete(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}
