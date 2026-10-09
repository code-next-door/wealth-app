package io.github.codenextdoor.wealth.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import java.time.LocalDate

/** Settings › Backup: transactions or balance history as a CSV file for a spreadsheet. */
@Composable
fun ExportSection(viewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    val messages = mapOf(
        ExportMessage.TRANSACTIONS_EXPORTED to stringResource(R.string.export_transactions_done),
        ExportMessage.BALANCES_EXPORTED to stringResource(R.string.export_balances_done),
        ExportMessage.FAILED to stringResource(R.string.export_failed),
    )
    LaunchedEffect(state.message) {
        state.message?.let {
            appMessages.show(messages.getValue(it))
            viewModel.messageShown()
        }
    }

    val transactionsFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let(viewModel::exportTransactions)
    }
    val balancesFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let(viewModel::exportBalances)
    }

    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(
        headlineContent = { Text(stringResource(R.string.export_transactions)) },
        supportingContent = { Text(stringResource(R.string.export_transactions_summary)) },
        colors = itemColors,
        modifier = Modifier.clickable(enabled = !state.busy) {
            viewModel.beforeFilePicker()
            transactionsFile.launch("wealth-transactions-${LocalDate.now()}.csv")
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.export_balances)) },
        supportingContent = { Text(stringResource(R.string.export_balances_summary)) },
        colors = itemColors,
        modifier = Modifier.clickable(enabled = !state.busy) {
            viewModel.beforeFilePicker()
            balancesFile.launch("wealth-balances-${LocalDate.now()}.csv")
        },
    )
}

private const val CSV = "text/csv"
