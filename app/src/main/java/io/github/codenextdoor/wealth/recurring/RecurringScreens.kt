package io.github.codenextdoor.wealth.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.accounts.BalanceDatePicker
import io.github.codenextdoor.wealth.accounts.DateField
import io.github.codenextdoor.wealth.expenses.CategoryField
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecurringListRoute(
    onBack: () -> Unit,
    onOpen: (id: Long?) -> Unit,
    viewModel: RecurringListViewModel = viewModel(factory = RecurringListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringListScreen(state, onBack, onOpen)
}

/** Every recurring expense, with how often and when it's next added. */
@Composable
fun RecurringListScreen(state: RecurringListUiState, onBack: () -> Unit, onOpen: (id: Long?) -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.recurring_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(null) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.recurring_add)) },
                text = { Text(stringResource(R.string.recurring_add)) },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.recurring_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (state.rows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.recurring_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.rows, key = { it.id }) { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(row.description) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    pluralStringResource(R.plurals.recurring_every, row.intervalMonths, row.intervalMonths),
                                    row.categoryName,
                                    row.nextDate?.let { stringResource(R.string.recurring_next, it.format(dateFormat)) }
                                        ?: stringResource(R.string.recurring_ended),
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = { Text(row.amountText, style = MaterialTheme.typography.titleSmall) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun RecurringEditRoute(
    itemId: Long?,
    onDone: () -> Unit,
    viewModel: RecurringEditViewModel = viewModel(factory = RecurringEditViewModel.factory(itemId)),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    RecurringEditScreen(
        state = state,
        fields = viewModel.fields,
        onBack = onDone,
        onCurrencyChange = viewModel::onCurrencyChange,
        onCategoryChange = viewModel::onCategoryChange,
        onAddCategory = viewModel::addCategory,
        onAccountChange = viewModel::onAccountChange,
        onIntervalChange = viewModel::onIntervalChange,
        onStartDateChange = viewModel::onStartDateChange,
        onEndDateChange = viewModel::onEndDateChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

private enum class PickingDate { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditScreen(
    state: RecurringEditUiState,
    fields: RecurringTextFields,
    onBack: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onAddCategory: (String) -> Unit,
    onAccountChange: (Long?) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartDateChange: (java.time.LocalDate) -> Unit,
    onEndDateChange: (java.time.LocalDate?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<PickingDate?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val required = stringResource(R.string.error_required)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.recurring_add else R.string.recurring_edit)) },
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
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                state = fields.description,
                label = { Text(stringResource(R.string.recurring_description_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.descriptionError,
                supportingText = if (state.descriptionError) ({ Text(required) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = fields.amount,
                label = { Text(stringResource(R.string.expense_amount_label)) },
                suffix = { Text(state.currencyCode) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.amountError,
                supportingText = if (state.amountError) ({ Text(stringResource(R.string.recurring_amount_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.account_currency_label),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = state.currencyCode,
                onSelect = onCurrencyChange,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryField(
                categories = state.categories,
                selected = state.categoryId,
                onSelect = onCategoryChange,
                onAdd = onAddCategory,
                modifier = Modifier.fillMaxWidth(),
            )
            state.ruleKeyword?.let {
                Text(stringResource(R.string.expense_category_by_rule, it), style = MaterialTheme.typography.bodySmall)
            }
            DropdownField(
                label = stringResource(R.string.expense_account_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.expense_no_account))) +
                    state.accounts.map { DropdownOption<Long?>(it.id, "${it.name} · ${it.currencyCode}") },
                selected = state.accountId,
                onSelect = onAccountChange,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.recurring_interval_label),
                options = RecurringEditViewModel.INTERVALS.map { DropdownOption(it, pluralStringResource(R.plurals.recurring_every, it, it)) },
                selected = state.intervalMonths,
                onSelect = onIntervalChange,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(stringResource(R.string.recurring_start_label), state.startDate, dateFormat, onClick = { picking = PickingDate.START })
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.endDate != null) {
                    DateField(
                        stringResource(R.string.recurring_end_label),
                        state.endDate,
                        dateFormat,
                        onClick = { picking = PickingDate.END },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEndDateChange(null) }) { Text(stringResource(R.string.recurring_no_end)) }
                } else {
                    TextButton(onClick = { picking = PickingDate.END }) { Text(stringResource(R.string.recurring_add_end)) }
                }
            }
            if (state.endDateError) {
                Text(stringResource(R.string.recurring_end_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (state.dueNow > 0) {
                        Text(pluralStringResource(R.plurals.recurring_due_now, state.dueNow, state.dueNow), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        state.nextDate?.let { stringResource(R.string.recurring_next_added, it.format(dateFormat)) }
                            ?: stringResource(R.string.recurring_no_more),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.recurring_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }

    picking?.let { which ->
        BalanceDatePicker(
            initial = (if (which == PickingDate.START) state.startDate else state.endDate) ?: state.startDate,
            onPick = {
                if (which == PickingDate.START) onStartDateChange(it) else onEndDateChange(it)
                picking = null
            },
            onDismiss = { picking = null },
            allowFuture = true,
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = fields.description.text.toString(),
            message = stringResource(R.string.recurring_delete_message),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
