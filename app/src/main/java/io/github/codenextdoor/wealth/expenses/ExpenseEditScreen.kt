package io.github.codenextdoor.wealth.expenses

import androidx.compose.runtime.key
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.accounts.BalanceDatePicker
import io.github.codenextdoor.wealth.accounts.DateField
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ExpenseEditRoute(
    onDone: () -> Unit,
    viewModel: ExpenseEditViewModel = viewModel(factory = ExpenseEditViewModel.Factory),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    ExpenseEditScreen(
        state = state,
        fields = viewModel.fields,
        onBack = onDone,
        actions = ExpenseEditActions(
            onDateChange = viewModel::onDateChange,
            onCategoryChange = viewModel::onCategoryChange,
            onAccountChange = viewModel::onAccountChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onAcceptRule = viewModel::acceptRule,
            onDeclineRule = viewModel::declineRule,
        ),
    )
}

/** Everything the expense form can ask for, bundled to keep the screen's signature short. */
data class ExpenseEditActions(
    val onDateChange: (java.time.LocalDate) -> Unit,
    val onCategoryChange: (Long?) -> Unit,
    val onAccountChange: (Long?) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onAcceptRule: (keyword: String) -> Unit,
    val onDeclineRule: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(state: ExpenseEditUiState, fields: ExpenseTextFields, onBack: () -> Unit, actions: ExpenseEditActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val required = stringResource(R.string.error_required)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.expense_add else R.string.expense_edit)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                state = fields.description,
                label = { Text(stringResource(R.string.expense_description_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.descriptionError,
                supportingText = if (state.descriptionError) ({ Text(required) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = fields.amount,
                label = { Text(stringResource(R.string.expense_amount_label)) },
                suffix = { form.currencyCode?.let { Text(it) } },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.amountError,
                supportingText = {
                    Text(
                        when {
                            !state.amountError -> stringResource(R.string.expense_amount_hint)
                            form.amountText.isBlank() -> required
                            else -> stringResource(R.string.account_balance_invalid, state.selectedCurrency?.decimals ?: 2)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.balance_entry_date),
                date = form.date,
                format = dateFormat,
                onClick = { pickDate = true },
            )
            DropdownField(
                label = stringResource(R.string.expense_category_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.expenses_uncategorized))) +
                    state.categories.map { DropdownOption<Long?>(it.id, it.name) },
                selected = form.categoryId,
                onSelect = actions.onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
            )
            state.matchedRule?.let {
                Text(
                    stringResource(R.string.expense_category_by_rule, it.keyword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            DropdownField(
                label = stringResource(R.string.expense_account_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.expense_no_account))) +
                    state.accounts.map { DropdownOption<Long?>(it.id, it.name) },
                selected = form.accountId,
                onSelect = actions.onAccountChange,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.account_currency_label),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = form.currencyCode,
                onSelect = actions.onCurrencyChange,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = fields.note,
                label = { Text(stringResource(R.string.account_note_label)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions.onSave, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickDate) {
        BalanceDatePicker(initial = form.date, onPick = { actions.onDateChange(it); pickDate = false }, onDismiss = { pickDate = false })
    }

    state.ruleSuggestion?.let { suggestion ->
        // A fresh field per suggestion, starting from the suggested keyword.
        val keyword = key(suggestion) { rememberTextFieldState(suggestion.keyword) }
        AlertDialog(
            onDismissRequest = actions.onDeclineRule,
            title = { Text(stringResource(R.string.rule_suggestion_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rule_suggestion_message, suggestion.categoryName))
                    OutlinedTextField(
                        state = keyword,
                        label = { Text(stringResource(R.string.rule_keyword_label)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actions.onAcceptRule(keyword.text.toString()) }, enabled = keyword.text.isNotBlank()) {
                    Text(stringResource(R.string.rule_suggestion_accept))
                }
            },
            dismissButton = { TextButton(onClick = actions.onDeclineRule) { Text(stringResource(R.string.rule_suggestion_decline)) } },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = form.description,
            message = stringResource(R.string.delete_message_generic),
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
