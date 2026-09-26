package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import io.github.codenextdoor.wealth.domain.Currency
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
    onInstitutionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
        }
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
            onCountryChange = {}, onBalanceChange = {}, onInstitutionChange = {}, onNoteChange = {},
            onSave = {}, onDelete = {},
        )
    }
}
