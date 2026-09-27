package io.github.codenextdoor.wealth.grants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.accounts.BalanceDatePicker
import io.github.codenextdoor.wealth.accounts.DateField
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GrantEditRoute(
    onDone: () -> Unit,
    viewModel: GrantEditViewModel = viewModel(factory = GrantEditViewModel.Factory),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    GrantEditScreen(
        state = state,
        fields = viewModel.fields,
        onBack = onDone,
        onCurrencyChange = viewModel::onCurrencyChange,
        onGrantDateChange = viewModel::onGrantDateChange,
        onVestStartChange = viewModel::onVestStartChange,
        onIntervalChange = viewModel::onIntervalChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** Which date field's picker is open. */
private enum class GrantDate { GRANT, VEST_START }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrantEditScreen(
    state: GrantEditUiState,
    fields: GrantTextFields,
    onBack: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onGrantDateChange: (LocalDate) -> Unit,
    onVestStartChange: (LocalDate) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<GrantDate?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.grant_add else R.string.grant_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isReady) return@Scaffold
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.grant_intro), style = MaterialTheme.typography.bodyMedium)
            GrantField(fields.name, stringResource(R.string.grant_name_label), state.nameError, stringResource(R.string.error_required))
            GrantField(
                fields.symbol, stringResource(R.string.grant_symbol_label), state.symbolError, stringResource(R.string.error_required),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            DropdownField(
                label = stringResource(R.string.grant_currency_label),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = state.currencyCode,
                onSelect = onCurrencyChange,
                modifier = Modifier.fillMaxWidth(),
            )
            GrantField(
                fields.units, stringResource(R.string.grant_units_label), state.unitsError, stringResource(R.string.grant_units_error),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            DateField(stringResource(R.string.grant_date_label), state.grantDate, dateFormat, onClick = { picking = GrantDate.GRANT })
            DateField(stringResource(R.string.grant_vest_start_label), state.vestStart, dateFormat, onClick = { picking = GrantDate.VEST_START })
            GrantField(
                fields.months, stringResource(R.string.grant_months_label), state.monthsError,
                stringResource(R.string.grant_months_error, state.intervalMonths),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            DropdownField(
                label = stringResource(R.string.grant_interval_label),
                options = GrantEditViewModel.INTERVALS.map { DropdownOption(it, pluralStringResource(R.plurals.grant_every_months, it, it)) },
                selected = state.intervalMonths,
                onSelect = onIntervalChange,
                modifier = Modifier.fillMaxWidth(),
            )
            GrantField(
                fields.cliff, stringResource(R.string.grant_cliff_label), state.cliffError, stringResource(R.string.grant_cliff_error),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                hint = stringResource(R.string.grant_cliff_hint),
            )
            GrantField(fields.note, stringResource(R.string.account_note_label), isError = false, errorText = "")

            state.preview?.let { preview ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            pluralStringResource(R.plurals.grant_preview_vests, preview.vestCount, preview.vestCount, preview.unitsPerVest, preview.symbol),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.grant_preview_dates, preview.firstVest.format(dateFormat), preview.lastVest.format(dateFormat)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.grant_preview_vested, preview.vestedSoFar, preview.symbol),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }

    picking?.let { which ->
        BalanceDatePicker(
            initial = if (which == GrantDate.GRANT) state.grantDate else state.vestStart,
            onPick = {
                if (which == GrantDate.GRANT) onGrantDateChange(it) else onVestStartChange(it)
                picking = null
            },
            onDismiss = { picking = null },
            allowFuture = which == GrantDate.VEST_START,
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = fields.name.text.toString(),
            message = stringResource(R.string.grant_delete_message),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun GrantField(
    state: TextFieldState,
    label: String,
    isError: Boolean,
    errorText: String,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    hint: String? = null,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        isError = isError,
        supportingText = when {
            isError -> { { Text(errorText) } }
            hint != null -> { { Text(hint) } }
            else -> null
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}
