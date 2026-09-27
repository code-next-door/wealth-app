package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.ui.LocalAppMessages
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** File types offered in the picker; banks label CSV exports inconsistently. */
private val STATEMENT_TYPES = arrayOf("application/pdf", "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

@Composable
fun ImportRoute(
    onDone: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri != null -> viewModel.load(uri)
            // Cancelled before any file was chosen: nothing to review.
            state.stage == ImportStage.PICKING -> onDone()
        }
    }
    // Open the file picker straight away (once, not again after rotation).
    var pickerShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!pickerShown) {
            pickerShown = true
            viewModel.beforeFilePicker()
            picker.launch(STATEMENT_TYPES)
        }
    }
    val importedMessage = when {
        state.holdingsSaved -> stringResource(R.string.import_holdings_saved)
        else -> state.importedCount?.let { pluralStringResource(R.plurals.import_done, it, it) }
    }
    LaunchedEffect(importedMessage) {
        if (importedMessage != null) {
            messages.show(importedMessage)
            onDone()
        }
    }
    ImportScreen(
        state = state,
        onBack = onDone,
        onPickAnother = {
            viewModel.beforeFilePicker()
            picker.launch(STATEMENT_TYPES)
        },
        onSelectAccount = viewModel::selectAccount,
        onInclude = viewModel::setInclude,
        onCategory = viewModel::setCategory,
        onRecordClosing = viewModel::setRecordClosingBalance,
        onMappingChange = viewModel::updateMapping,
        onImport = viewModel::import,
    )
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onInclude: (index: Int, include: Boolean) -> Unit,
    onCategory: (index: Int, categoryId: Long?) -> Unit,
    onRecordClosing: (Boolean) -> Unit,
    onMappingChange: ((CsvMapping) -> CsvMapping) -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.import_title), onBack) },
        bottomBar = {
            if (state.stage == ImportStage.REVIEW) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        if (state.holdings != null) {
                            Button(
                                onClick = onImport,
                                enabled = state.accountId != null && state.holdings.date != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.import_holdings_button)) }
                        } else {
                            Text(
                                pluralStringResource(R.plurals.import_summary, state.includedCount, state.includedCount, state.includedTotalText),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Button(onClick = onImport, enabled = state.includedCount > 0, modifier = Modifier.fillMaxWidth()) {
                                Text(pluralStringResource(R.plurals.import_button, state.includedCount, state.includedCount))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (state.stage) {
            ImportStage.PICKING, ImportStage.LOADING, ImportStage.IMPORTING -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.stage != ImportStage.PICKING) CircularProgressIndicator()
            }
            ImportStage.ERROR -> ErrorContent(state.error, padding, onPickAnother)
            ImportStage.REVIEW -> ReviewContent(state, padding, onSelectAccount, onInclude, onCategory, onRecordClosing, onMappingChange)
        }
    }
}

@Composable
private fun ErrorContent(error: ImportError?, padding: PaddingValues, onPickAnother: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                when (error) {
                    ImportError.UNREADABLE -> R.string.import_error_unreadable
                    ImportError.UNKNOWN_PDF -> R.string.import_error_unknown_pdf
                    ImportError.NO_TRANSACTIONS -> R.string.import_error_no_transactions
                    ImportError.PDF_NOT_SUPPORTED -> R.string.import_error_pdf_not_supported
                    null -> R.string.import_no_file
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.import_pick_another))
        }
    }
}

@Composable
private fun ReviewContent(
    state: ImportUiState,
    padding: PaddingValues,
    onSelectAccount: (Long?) -> Unit,
    onInclude: (Int, Boolean) -> Unit,
    onCategory: (Int, Long?) -> Unit,
    onRecordClosing: (Boolean) -> Unit,
    onMappingChange: ((CsvMapping) -> CsvMapping) -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Text(state.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val first = state.rows.minOfOrNull { it.date }
                val last = state.rows.maxOfOrNull { it.date }
                Text(
                    listOfNotNull(
                        state.format,
                        pluralStringResource(R.plurals.import_rows, state.rows.size, state.rows.size),
                        if (first != null && last != null) "${first.format(dateFormat)} – ${last.format(dateFormat)}" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                DropdownField(
                    label = stringResource(R.string.import_account_label),
                    options = listOf(DropdownOption<Long?>(null, stringResource(R.string.expense_no_account))) +
                        state.accounts.map { DropdownOption<Long?>(it.id, "${it.name} · ${it.currencyCode}") },
                    selected = state.accountId,
                    onSelect = onSelectAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.statementCurrency?.let {
                    Text(
                        stringResource(R.string.import_currency_mismatch, it, state.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.holdings?.let { holdings ->
                    Text(
                        stringResource(
                            R.string.import_holdings_summary,
                            holdings.sharesText,
                            holdings.cashText,
                            holdings.totalText,
                            holdings.date?.format(dateFormat).orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (!holdings.addsUp) {
                        Text(
                            stringResource(R.string.import_holdings_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.accounts.isEmpty()) {
                        Text(
                            stringResource(R.string.import_holdings_no_account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.holdings == null && state.closingBalanceText != null && state.closingDate != null && state.accountId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecordClosing(!state.recordClosingBalance) }
                            .padding(top = 8.dp),
                    ) {
                        Checkbox(checked = state.recordClosingBalance, onCheckedChange = onRecordClosing)
                        Text(
                            stringResource(R.string.import_record_closing, state.closingBalanceText, state.closingDate.format(dateFormat)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        state.csvMapping?.let { mapping ->
            item { CsvMappingCard(mapping, state.csvHeaders, onMappingChange) }
        }

        if (state.holdings == null) item {
            Text(
                stringResource(R.string.import_rows_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        items(state.rows, key = { it.index }) { row ->
            ImportRowCard(row, state.categories, dateFormat, onInclude, onCategory)
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ImportRowCard(
    row: ImportRow,
    categories: List<ExpenseCategory>,
    dateFormat: DateTimeFormatter,
    onInclude: (Int, Boolean) -> Unit,
    onCategory: (Int, Long?) -> Unit,
) {
    val faded = !row.include
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (faded) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = row.include, onCheckedChange = { onInclude(row.index, it) })
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 12.dp),
            ) {
                Row {
                    Text(
                        row.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.amountText,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val notes = listOfNotNull(
                    row.date.format(dateFormat),
                    if (row.moneyIn) stringResource(R.string.import_badge_money_in) else null,
                    if (row.isDuplicate) stringResource(R.string.import_badge_duplicate) else null,
                    row.recurringMatch?.let { stringResource(R.string.import_badge_recurring, it) },
                    if (row.skippedByRule) stringResource(R.string.import_badge_skip_rule, row.ruleKeyword.orEmpty()) else null,
                )
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.needsCheck) {
                    Text(
                        stringResource(R.string.import_badge_check),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                CategoryChip(row, categories, onCategory)
            }
        }
    }
}

@Composable
private fun CategoryChip(row: ImportRow, categories: List<ExpenseCategory>, onCategory: (Int, Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = categories.firstOrNull { it.id == row.categoryId }?.name ?: stringResource(R.string.expenses_uncategorized)
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(name) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.expenses_uncategorized)) },
                onClick = { onCategory(row.index, null); open = false },
            )
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = { onCategory(row.index, category.id); open = false })
            }
        }
    }
}

/** Lets the user fix the column guess for CSV files. */
@Composable
private fun CsvMappingCard(mapping: CsvMapping, headers: List<String>, onChange: ((CsvMapping) -> CsvMapping) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.import_columns_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.import_columns_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!expanded) return@SectionCard

        val none = stringResource(R.string.import_column_none)
        val columns = headers.mapIndexed { i, h -> DropdownOption<Int?>(i, h.ifBlank { "#${i + 1}" }) }
        val optional = listOf(DropdownOption<Int?>(null, none)) + columns
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DropdownField(stringResource(R.string.import_column_date), columns, mapping.dateColumn, { c -> c?.let { onChange { m -> m.copy(dateColumn = it) } } }, Modifier.fillMaxWidth())
            DropdownField(
                stringResource(R.string.import_column_date_format),
                CsvStatementParser.DATE_PATTERNS.map { DropdownOption(it, it) },
                mapping.datePattern,
                { p -> onChange { it.copy(datePattern = p) } },
                Modifier.fillMaxWidth(),
            )
            DropdownField(
                stringResource(R.string.import_column_description),
                columns,
                mapping.descriptionColumns.firstOrNull(),
                { c -> c?.let { onChange { m -> m.copy(descriptionColumns = listOf(it) + m.descriptionColumns.filter { d -> d != it }) } } },
                Modifier.fillMaxWidth(),
            )
            DropdownField(stringResource(R.string.import_column_amount), optional, mapping.amountColumn, { c -> onChange { it.copy(amountColumn = c) } }, Modifier.fillMaxWidth())
            DropdownField(stringResource(R.string.import_column_debit), optional, mapping.debitColumn, { c -> onChange { it.copy(debitColumn = c) } }, Modifier.fillMaxWidth())
            DropdownField(stringResource(R.string.import_column_credit), optional, mapping.creditColumn, { c -> onChange { it.copy(creditColumn = c) } }, Modifier.fillMaxWidth())
            if (mapping.amountColumn != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.import_positive_is_spending), modifier = Modifier.weight(1f))
                    Switch(checked = !mapping.negativeIsMoneyOut, onCheckedChange = { v -> onChange { it.copy(negativeIsMoneyOut = !v) } })
                }
            }
        }
    }
}
