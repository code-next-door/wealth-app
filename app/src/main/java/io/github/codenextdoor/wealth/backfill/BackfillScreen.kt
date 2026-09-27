package io.github.codenextdoor.wealth.backfill

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.imports.STATEMENT_TYPES
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackfillRoute(
    onDone: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: BackfillViewModel = viewModel(factory = BackfillViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> viewModel.load(uris) }
    val pick = {
        viewModel.beforeFilePicker()
        picker.launch(STATEMENT_TYPES)
    }
    // Open the file picker straight away (once, not again after rotation).
    var pickerShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!pickerShown) {
            pickerShown = true
            pick()
        }
    }
    val done = if (state.stage == BackfillStage.DONE) {
        val balances = pluralStringResource(R.plurals.backfill_done, state.savedPoints ?: 0, state.savedPoints ?: 0, state.savedAccounts ?: 0)
        val spending = state.savedExpenses?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backfill_done_expenses, it, it) }
        listOfNotNull(balances, spending).joinToString(" ")
    } else {
        null
    }
    LaunchedEffect(done) {
        if (done != null) {
            messages.show(done)
            onDone()
        }
    }
    BackfillScreen(
        state = state,
        onBack = onDone,
        onPick = pick,
        onAccount = viewModel::setAccount,
        onAddExpenses = viewModel::setAddExpenses,
        onAddAccount = onAddAccount,
        onSave = viewModel::save,
    )
}

@Composable
fun BackfillScreen(
    state: BackfillUiState,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onAccount: (key: String, accountId: Long?) -> Unit,
    onAddExpenses: (Boolean) -> Unit,
    onAddAccount: () -> Unit,
    onSave: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.backfill_title), onBack) },
        bottomBar = {
            if (state.stage == BackfillStage.REVIEW) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onSave,
                        enabled = state.pointCount > 0,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .fillMaxWidth(),
                    ) { Text(pluralStringResource(R.plurals.backfill_button, state.pointCount, state.pointCount)) }
                }
            }
        },
    ) { padding ->
        if (state.stage == BackfillStage.SAVING) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.backfill_intro), style = MaterialTheme.typography.bodyMedium) }
            items(state.files, key = { it.key }) { file ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val status = when (file.status) {
                            BackfillStatus.READING -> stringResource(R.string.backfill_reading)
                            BackfillStatus.UNREADABLE -> stringResource(R.string.import_error_unreadable)
                            BackfillStatus.UNKNOWN_LAYOUT -> stringResource(R.string.backfill_unknown_layout)
                            BackfillStatus.PDF_NOT_SUPPORTED -> stringResource(R.string.import_error_pdf_not_supported)
                            BackfillStatus.NO_HISTORY -> stringResource(R.string.backfill_no_history)
                            BackfillStatus.READY -> listOfNotNull(
                                file.format,
                                if (file.from != null && file.to != null) {
                                    pluralStringResource(R.plurals.backfill_points, file.pointCount, file.pointCount, file.from.format(dateFormat), file.to.format(dateFormat))
                                } else {
                                    null
                                },
                            ).joinToString(" · ")
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (file.status == BackfillStatus.READY || file.status == BackfillStatus.READING) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        if (file.valueNeedsCheck) {
                            Text(stringResource(R.string.import_value_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (file.status == BackfillStatus.READY) {
                            if (file.accounts.isEmpty()) {
                                Text(stringResource(R.string.backfill_no_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onAddAccount) { Text(stringResource(R.string.account_add)) }
                            } else {
                                DropdownField(
                                    label = stringResource(R.string.backfill_account_label),
                                    options = listOf(DropdownOption<Long?>(null, stringResource(R.string.backfill_skip))) +
                                        file.accounts.map { DropdownOption<Long?>(it.id, "${it.name} · ${it.currencyCode}") },
                                    selected = file.accountId,
                                    onSelect = { onAccount(file.key, it) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.files.isEmpty()) R.string.backfill_choose else R.string.backfill_add_more))
                }
            }
            if (state.files.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = state.addExpenses, role = Role.Switch, onValueChange = onAddExpenses),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.backfill_add_expenses), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.backfill_add_expenses_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.addExpenses, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
