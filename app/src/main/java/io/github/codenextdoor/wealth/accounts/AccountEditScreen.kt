package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.components.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

@Composable
fun AccountEditRoute(
    onDone: () -> Unit,
    viewModel: AccountEditViewModel = viewModel(factory = AccountEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    AccountEditScreen(
        state = state,
        onBack = onDone,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onCountryChange = viewModel::onCountryChange,
        onBalanceChange = viewModel::onBalanceChange,
        onBalanceDateChange = viewModel::onBalanceDateChange,
        onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
        onInstitutionChange = viewModel::onInstitutionChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    state: AccountEditUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (Long) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onCountryChange: (Long?) -> Unit,
    onBalanceChange: (String) -> Unit,
    onBalanceDateChange: (LocalDate) -> Unit,
    onDeleteHistoryEntry: (entryId: Long) -> Unit,
    onInstitutionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var deleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val form = state.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.account_add else R.string.account_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isReady) return@Scaffold

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val required = stringResource(R.string.error_required)

            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.account_name_label)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(required) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = stringResource(R.string.account_type_label),
                options = state.types.map { type ->
                    val group = when {
                        type.kind == AssetKind.LIABILITY -> stringResource(R.string.kind_liability)
                        else -> state.countries.firstOrNull { it.id == type.countryId }?.name
                            ?: stringResource(R.string.account_types_section_general)
                    }
                    DropdownOption(type.id, stringResource(R.string.account_type_option, type.name, group))
                },
                selected = form.typeId,
                onSelect = onTypeChange,
                errorText = if (state.typeError) required else null,
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = stringResource(R.string.account_currency_label),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = form.currencyCode,
                onSelect = onCurrencyChange,
                errorText = if (state.currencyError) required else null,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.balanceText,
                onValueChange = onBalanceChange,
                label = {
                    Text(stringResource(if (state.isLiability) R.string.account_owed_label else R.string.account_balance_label))
                },
                suffix = { form.currencyCode?.let { Text(it) } },
                singleLine = true,
                isError = state.balanceError,
                supportingText = when {
                    !state.balanceError -> null
                    form.balanceText.isBlank() -> ({ Text(required) })
                    else -> ({ Text(stringResource(R.string.account_balance_invalid, state.selectedCurrency?.decimals ?: 2)) })
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // Read-only field that opens a date picker; the overlay catches the tap.
            Box {
                OutlinedTextField(
                    value = form.balanceDate.format(dateFormat),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.account_balance_date_label)) },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Carries the field's label and value for screen readers, since the
                // overlay sits on top of the text field.
                val dateDescription = stringResource(R.string.account_balance_date_label) + ": " +
                    form.balanceDate.format(dateFormat)
                Box(
                    Modifier
                        .matchParentSize()
                        .semantics { contentDescription = dateDescription }
                        .clickable(role = Role.Button) { pickDate = true },
                )
            }

            DropdownField(
                label = stringResource(R.string.account_country_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.country_general))) +
                    state.countries.map { DropdownOption<Long?>(it.id, it.name) },
                selected = form.countryId,
                onSelect = onCountryChange,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.institution,
                onValueChange = onInstitutionChange,
                label = { Text(stringResource(R.string.account_institution_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.account_note_label)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }

            if (state.history.isNotEmpty()) {
                BalanceHistory(
                    entries = state.history,
                    currency = state.selectedCurrency,
                    dateFormat = dateFormat,
                    onDelete = { deleteEntryId = it },
                )
            }
        }
    }

    if (pickDate) {
        BalanceDatePicker(
            initial = form.balanceDate,
            onPick = { onBalanceDateChange(it); pickDate = false },
            onDismiss = { pickDate = false },
        )
    }

    deleteEntryId?.let { id ->
        val entry = state.history.firstOrNull { it.id == id }
        ConfirmDeleteDialog(
            itemName = entry?.date?.format(dateFormat).orEmpty(),
            message = stringResource(R.string.account_history_delete_message),
            onConfirm = { onDeleteHistoryEntry(id); deleteEntryId = null },
            onDismiss = { deleteEntryId = null },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = form.name,
            message = stringResource(R.string.delete_message_generic),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BalanceHistory(
    entries: List<BalanceEntry>,
    currency: Currency?,
    dateFormat: DateTimeFormatter,
    onDelete: (entryId: Long) -> Unit,
) {
    SectionHeader(stringResource(R.string.account_history_title))
    Text(
        stringResource(R.string.account_history_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    entries.forEach { entry ->
        ListItem(
            headlineContent = {
                val decimals = currency?.decimals ?: 2
                Text(formatMoney(minorToDecimal(entry.balanceMinor, decimals), currency?.code ?: "", decimals))
            },
            supportingContent = { Text(entry.date.format(dateFormat)) },
            trailingContent = if (entries.size > 1) {
                {
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.account_history_delete))
                    }
                }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material date picker works in UTC midnight milliseconds.
    val todayMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountEditScreenPreview() {
    WealthTheme {
        AccountEditScreen(
            state = AccountEditUiState(
                isNew = true,
                isReady = true,
                form = AccountForm(name = "NRE savings", typeId = 1, currencyCode = "INR", countryId = 2, balanceText = "840000"),
                types = listOf(AccountType(1, "NRE account", AssetKind.ASSET, 2)),
                countries = listOf(Country(1, "Switzerland"), Country(2, "India")),
                currencies = listOf(Currency("CHF", "Swiss Franc", 2), Currency("INR", "Indian Rupee", 2)),
            ),
            onBack = {}, onNameChange = {}, onTypeChange = {}, onCurrencyChange = {},
            onCountryChange = {}, onBalanceChange = {}, onBalanceDateChange = {}, onDeleteHistoryEntry = {},
            onInstitutionChange = {}, onNoteChange = {},
            onSave = {}, onDelete = {},
        )
    }
}
