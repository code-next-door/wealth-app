package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.components.SectionHeader
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

    LazyColumn(contentPadding = contentPadding) {
        if (state.assets.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.accounts_section_assets)) }
            items(state.assets, key = { it.id }) { AccountListItem(it, onOpenAccount) }
        }
        if (state.liabilities.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.accounts_section_liabilities)) }
            items(state.liabilities, key = { it.id }) { AccountListItem(it, onOpenAccount) }
        }
        item { Spacer(Modifier.height(88.dp)) } // Keeps the last row clear of the button.
    }
}

@Composable
private fun AccountListItem(row: AccountRow, onOpen: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = if (row.details.isNotEmpty()) {
            { Text(row.details) }
        } else {
            null
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.balanceText, style = MaterialTheme.typography.bodyLarge)
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
            ),
            contentPadding = PaddingValues(),
            onOpenAccount = {},
        )
    }
}
