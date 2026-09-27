package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.formatRate
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DeleteBlockedDialog
import io.github.codenextdoor.wealth.ui.components.TextInputDialog
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.math.BigDecimal
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import androidx.compose.material3.FilledTonalButton

/** Connects [CurrenciesScreen] to its ViewModel. */
@Composable
fun CurrenciesRoute(
    onBack: () -> Unit,
    viewModel: CurrenciesViewModel = viewModel(factory = CurrenciesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteBlocked by viewModel.deleteBlocked.collectAsStateWithLifecycle()
    val refresh by viewModel.refresh.collectAsStateWithLifecycle()
    CurrenciesScreen(
        state = state,
        refresh = refresh,
        onRefresh = viewModel::refreshRates,
        deleteBlocked = deleteBlocked,
        onDismissDeleteBlocked = viewModel::dismissDeleteBlocked,
        onBack = onBack,
        onAdd = viewModel::addCurrency,
        onDelete = viewModel::deleteCurrency,
        onSetBase = viewModel::setBaseCurrency,
        onSetRate = viewModel::setRate,
    )
}

@Composable
fun CurrenciesScreen(
    state: CurrenciesUiState,
    refresh: RatesRefresh,
    onRefresh: () -> Unit,
    deleteBlocked: String?,
    onDismissDeleteBlocked: () -> Unit,
    onBack: () -> Unit,
    onAdd: (code: String) -> AddCurrencyResult,
    onDelete: (code: String) -> Unit,
    onSetBase: (code: String) -> Unit,
    onSetRate: (from: String, to: String, input: String) -> Boolean,
) {
    // Which dialog is open. Saveable so it survives screen rotation.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var rateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_currencies_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.currency_add)) },
                text = { Text(stringResource(R.string.currency_add)) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    text = stringResource(R.string.currencies_intro, state.baseCurrency),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    FilledTonalButton(onClick = onRefresh, enabled = refresh != RatesRefresh.RUNNING) {
                        Text(stringResource(R.string.rates_refresh))
                    }
                    val status = when (refresh) {
                        RatesRefresh.IDLE -> null
                        RatesRefresh.RUNNING -> R.string.rates_refresh_running
                        RatesRefresh.DONE -> R.string.rates_refresh_done
                        RatesRefresh.OFFLINE -> R.string.rates_refresh_offline
                    }
                    if (status != null) {
                        Text(
                            stringResource(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            items(state.rows, key = { it.code }) { row ->
                ListItem(
                    headlineContent = { Text("${row.code} · ${row.name}") },
                    supportingContent = { Text(rateDescription(row, state.baseCurrency)) },
                    trailingContent = if (row.isBase) {
                        { SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.currency_base_badge)) }) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { actionsFor = row.code },
                )
            }
            item { Spacer(Modifier.height(88.dp)) } // Keeps the last row clear of the button.
        }
    }

    if (showAdd) {
        var error by rememberSaveable { mutableStateOf<AddCurrencyResult?>(null) }
        TextInputDialog(
            title = stringResource(R.string.currency_add),
            label = stringResource(R.string.currency_code_label),
            confirmLabel = stringResource(R.string.action_add),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            errorText = when (error) {
                AddCurrencyResult.INVALID_CODE -> stringResource(R.string.currency_error_invalid)
                AddCurrencyResult.ALREADY_EXISTS -> stringResource(R.string.currency_error_exists)
                else -> null
            },
            onConfirm = { code ->
                val result = onAdd(code)
                if (result == AddCurrencyResult.ADDED) showAdd = false else error = result
            },
            onDismiss = { showAdd = false },
        )
    }

    actionsFor?.let { code ->
        val row = state.rows.firstOrNull { it.code == code }
        if (row == null) {
            actionsFor = null
        } else {
            CurrencyActionsDialog(
                row = row,
                onEditRate = { actionsFor = null; rateFor = code },
                onSetBase = { actionsFor = null; onSetBase(code) },
                onDelete = { actionsFor = null; deleteFor = code },
                onDismiss = { actionsFor = null },
            )
        }
    }

    rateFor?.let { code ->
        val row = state.rows.firstOrNull { it.code == code }
        if (row == null) {
            rateFor = null
        } else {
            ExchangeRateDialog(
                row = row,
                baseCurrency = state.baseCurrency,
                onSave = { from, to, input -> onSetRate(from, to, input).also { if (it) rateFor = null } },
                onDismiss = { rateFor = null },
            )
        }
    }

    deleteBlocked?.let { code ->
        DeleteBlockedDialog(
            itemName = code,
            message = stringResource(R.string.delete_blocked_currency),
            onDismiss = onDismissDeleteBlocked,
        )
    }

    deleteFor?.let { code ->
        ConfirmDeleteDialog(
            itemName = code,
            message = stringResource(R.string.currency_delete_message),
            onConfirm = { onDelete(code); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun rateDescription(row: CurrencyRow, base: String): String {
    if (row.isBase) return stringResource(R.string.currency_is_base)
    val rate = row.rateToBase ?: return stringResource(R.string.currency_no_rate)
    val inverse = BigDecimal.ONE.divide(rate, CurrencyConverter.MATH)
    val text = stringResource(R.string.currency_rate_line, row.code, formatRate(rate), base) +
        " · " + stringResource(R.string.currency_rate_line, base, formatRate(inverse), row.code)
    val withNote = if (row.rateIsDerived) text + " " + stringResource(R.string.currency_rate_derived) else text
    val date = row.rateDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: return withNote
    return withNote + "\n" + stringResource(if (row.rateFetched) R.string.currency_rate_downloaded else R.string.currency_rate_typed, date)
}

@Composable
private fun CurrencyActionsDialog(
    row: CurrencyRow,
    onEditRate: () -> Unit,
    onSetBase: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.code} · ${row.name}") },
        text = {
            if (row.isBase) {
                Text(stringResource(R.string.currency_base_cannot_delete))
            } else {
                Column {
                    TextButton(onClick = onEditRate) { Text(stringResource(R.string.currency_action_edit_rate)) }
                    TextButton(onClick = onSetBase) { Text(stringResource(R.string.currency_action_set_base)) }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.currency_action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Lets the user enter the rate in whichever direction they think in:
 * "1 INR = 0.0095 CHF" or "1 CHF = 105.26 INR".
 */
@Composable
private fun ExchangeRateDialog(
    row: CurrencyRow,
    baseCurrency: String,
    onSave: (from: String, to: String, input: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var baseFirst by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val (from, to) = if (baseFirst) baseCurrency to row.code else row.code to baseCurrency
    val current = row.rateToBase?.let {
        formatRate(if (baseFirst) BigDecimal.ONE.divide(it, CurrencyConverter.MATH) else it)
    } ?: ""

    TextInputDialog(
        title = stringResource(R.string.rate_dialog_title, row.code),
        label = stringResource(R.string.rate_dialog_label, from, to),
        confirmLabel = stringResource(R.string.action_save),
        initialValue = current,
        errorText = if (invalid) stringResource(R.string.rate_error_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        onConfirm = { input -> invalid = !onSave(from, to, input) },
        onDismiss = onDismiss,
        header = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                listOf(false, true).forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = baseFirst == option,
                        onClick = { baseFirst = option; invalid = false },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) {
                        Text(if (option) "$baseCurrency → ${row.code}" else "${row.code} → $baseCurrency")
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CurrenciesScreenPreview() {
    WealthTheme {
        CurrenciesScreen(
            state = CurrenciesUiState(
                baseCurrency = "CHF",
                rows = listOf(
                    CurrencyRow("CHF", "Swiss Franc", isBase = true, rateToBase = null, rateIsDerived = false),
                    CurrencyRow("INR", "Indian Rupee", false, BigDecimal("0.0095"), rateIsDerived = false, java.time.LocalDate.of(2026, 3, 13), rateFetched = true),
                    CurrencyRow("USD", "US Dollar", false, null, rateIsDerived = false),
                ),
            ),
            refresh = RatesRefresh.DONE,
            onRefresh = {},
            deleteBlocked = null,
            onDismissDeleteBlocked = {},
            onBack = {},
            onAdd = { AddCurrencyResult.ADDED },
            onDelete = {},
            onSetBase = {},
            onSetRate = { _, _, _ -> true },
        )
    }
}
