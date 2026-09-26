package io.github.codenextdoor.wealth.accounts

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
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

@Composable
fun AccountsTab(
    contentPadding: PaddingValues,
    onOpenAccount: (id: Long) -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsContent(state, contentPadding, onOpenAccount)
}

@Composable
fun AccountsContent(
    state: AccountsUiState,
    contentPadding: PaddingValues,
    onOpenAccount: (id: Long) -> Unit,
) {
    if (!state.isLoading && state.assets.isEmpty() && state.liabilities.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.accounts_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp, // Clear of the add button.
        ),
    ) {
        if (state.assets.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.accounts_section_assets), state.assetsTotalText) }
            item { AccountGroup(state.assets, isLiability = false, onOpenAccount) }
        }
        if (state.liabilities.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.accounts_section_liabilities), state.liabilitiesTotalText) }
            item { AccountGroup(state.liabilities, isLiability = true, onOpenAccount) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, total: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(total, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
        leadingContent = { InitialBadge(row.name, isLiability) },
        headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (row.details.isNotEmpty()) {
            { Text(row.details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.balanceText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                when {
                    row.baseValueText != null -> Text(
                        stringResource(R.string.account_converted, row.baseValueText),
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

/** Round badge with the account's first letter; red-toned for debts. */
@Composable
private fun InitialBadge(name: String, isLiability: Boolean) {
    val container = if (isLiability) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isLiability) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
    ) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = content)
    }
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
