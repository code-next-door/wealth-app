package io.github.codenextdoor.wealth.accounts

import io.github.codenextdoor.wealth.ui.figure
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import io.github.codenextdoor.wealth.ui.tour.tourTarget
import io.github.codenextdoor.wealth.ui.tour.TourTarget
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.ui.components.AccountBadge
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import androidx.compose.material3.TextButton
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

@Composable
fun AccountsTab(
    contentPadding: PaddingValues,
    onOpenAccount: (id: Long) -> Unit,
    onOpenGrant: (id: Long?) -> Unit,
    onAddAccount: (AssetKind) -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsContent(state, contentPadding, onOpenAccount, onOpenGrant, onAddAccount)
}

@Composable
fun AccountsContent(
    state: AccountsUiState,
    contentPadding: PaddingValues,
    onOpenAccount: (id: Long) -> Unit,
    /** Opens a grant, or a new one for null. */
    onOpenGrant: (id: Long?) -> Unit = {},
    /** Adds an account from the Assets or Liabilities section. */
    onAddAccount: (AssetKind) -> Unit = {},
) {
    if (state.isLoading) return
    val nothingYet = state.assets.isEmpty() && state.liabilities.isEmpty() && state.grants.isEmpty()

    // One "+" per section, each adding its own kind of thing (no floating button).
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        if (nothingYet) {
            item {
                Text(
                    stringResource(R.string.accounts_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
                )
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.accounts_section_assets),
                state.assetsTotalText.takeIf { state.assets.isNotEmpty() },
                addLabel = stringResource(R.string.account_add_asset),
                onAdd = { onAddAccount(AssetKind.ASSET) },
                modifier = Modifier.tourTarget(TourTarget.ADD_ACCOUNT),
            )
        }
        item { if (state.assets.isEmpty()) EmptySection() else AccountGroup(state.assets, isLiability = false, onOpenAccount) }
        item {
            SectionTitle(
                stringResource(R.string.accounts_section_liabilities),
                state.liabilitiesTotalText.takeIf { state.liabilities.isNotEmpty() },
                addLabel = stringResource(R.string.account_add_liability),
                onAdd = { onAddAccount(AssetKind.LIABILITY) },
            )
        }
        item { if (state.liabilities.isEmpty()) EmptySection() else AccountGroup(state.liabilities, isLiability = true, onOpenAccount) }
        item {
            SectionTitle(
                stringResource(R.string.grants_section),
                state.unvestedTotalText.takeIf { state.grants.isNotEmpty() },
                addLabel = stringResource(R.string.grant_add),
                onAdd = { onOpenGrant(null) },
            )
        }
        item {
            Text(
                stringResource(R.string.grants_not_in_net_worth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        item { if (state.grants.isEmpty()) EmptySection() else GrantGroup(state.grants, onOpenGrant) }
    }
}

/** A section with nothing in it yet: a quiet line, its "+" is in the title. */
@Composable
private fun EmptySection() {
    Text(
        stringResource(R.string.accounts_section_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

/** Stock grants on one card: what's unvested, its value and the next vest. */
@Composable
private fun GrantGroup(rows: List<GrantRow>, onOpen: (Long?) -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            ListItem(
                headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.grant_unvested, row.unvestedUnits.figure(), row.totalUnits.figure(), row.symbol))
                        Text(
                            if (row.nextVestDate != null && row.nextVestUnits != null) {
                                stringResource(R.string.grant_next_vest, row.nextVestDate.format(dateFormat), row.nextVestUnits.figure(), row.symbol)
                            } else {
                                stringResource(R.string.grant_fully_vested)
                            },
                        )
                    }
                },
                trailingContent = row.valueText?.let { { Text(it.figure(), style = MaterialTheme.typography.titleSmall) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(row.id) },
            )
        }
    }
}

/** A section's title, its total (when it has rows) and its "+" to add one. */
@Composable
private fun SectionTitle(title: String, total: String?, addLabel: String, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        total?.let { Text(it.figure(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onAdd, modifier = modifier) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = addLabel)
        }
    }
}

/** A group of accounts on one rounded card. */
@Composable
private fun AccountGroup(rows: List<AccountRow>, isLiability: Boolean, onOpen: (Long) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { AccountListItem(it, isLiability, onOpen) }
    }
}

@Composable
private fun AccountListItem(row: AccountRow, isLiability: Boolean, onOpen: (Long) -> Unit) {
    ListItem(
        leadingContent = { AccountBadge(row.name, isLiability) },
        headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (row.details.isNotEmpty()) {
            { Text(row.details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.balanceText.figure(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                row.sharesText?.let {
                    Text(it.figure(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    row.notInNetWorth -> Text(
                        stringResource(R.string.account_house_loan_left_out),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.leftOut -> Text(
                        stringResource(R.string.account_left_out),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.missingPriceFor != null -> Text(
                        stringResource(R.string.account_missing_price, row.missingPriceFor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    row.baseValueText != null -> Text(
                        stringResource(R.string.account_converted, row.baseValueText.figure()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.missingRateFor != null -> Text(
                        stringResource(R.string.account_missing_rate, row.missingRateFor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onOpen(row.id) },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountsContentPreview() {
    WealthTheme {
        AccountsContent(
            state = AccountsUiState(
                isLoading = false,
                assets = listOf(
                    AccountRow(1, "Salary account", "Bank account · Switzerland · UBS", "CHF 12,500.00", null, null),
                    AccountRow(2, "NRE savings", "NRE account · India · HDFC", "₹8,40,000.00", "CHF 8,000.00", null),
                    AccountRow(3, "Brokerage", "Brokerage account", "$5,000.00", null, "USD"),
                ),
                liabilities = listOf(
                    AccountRow(4, "Credit card", "Credit card", "CHF 850.00", null, null),
                ),
                assetsTotalText = "CHF 20,500.00",
                liabilitiesTotalText = "CHF 850.00",
            ),
            contentPadding = PaddingValues(),
            onOpenAccount = {},
        )
    }
}
