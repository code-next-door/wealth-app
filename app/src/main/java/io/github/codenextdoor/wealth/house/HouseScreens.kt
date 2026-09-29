package io.github.codenextdoor.wealth.house

import io.github.codenextdoor.wealth.ui.figure
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.accounts.BalanceDatePicker
import io.github.codenextdoor.wealth.accounts.BalanceEntryDialog
import io.github.codenextdoor.wealth.accounts.DateField
import io.github.codenextdoor.wealth.accounts.RateSupport
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HouseTab(
    contentPadding: PaddingValues,
    onOpenHouse: (accountId: Long) -> Unit,
    viewModel: HouseListViewModel = viewModel(factory = HouseListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HouseContent(state, contentPadding, onOpenHouse, viewModel::setInNetWorth)
}

/** Houses with today's estimated value, and the switch that counts them in net worth. */
@Composable
fun HouseContent(
    state: HouseListUiState,
    contentPadding: PaddingValues,
    onOpenHouse: (accountId: Long) -> Unit,
    onInNetWorth: (Boolean) -> Unit,
) {
    if (state.isLoading) return
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp, // Clear of the add button.
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = state.inNetWorth, role = Role.Switch, onValueChange = onInNetWorth)
                        .padding(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.house_in_net_worth), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.house_in_net_worth_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.inNetWorth, onCheckedChange = null)
                }
            }
        }
        if (state.houses.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.house_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        }
        items(state.houses, key = { it.accountId }) { house ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHouse(house.accountId) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(house.name, style = MaterialTheme.typography.titleMedium)
                    Text(house.valueText.figure(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    house.baseValueText?.let { Text(stringResource(R.string.account_converted, it.figure()), style = MaterialTheme.typography.bodySmall) }
                    Text(
                        stringResource(R.string.house_bought, house.purchasePriceText.figure(), house.purchaseDate.format(dateFormat)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    house.yearlyGainPercent?.let {
                        Text(stringResource(R.string.house_yearly_gain, it.figure()), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.house_growth_after, house.growthPercent.figure(), house.lastValueDate.format(dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (house.loanName != null && house.equityText != null) {
                        Text(stringResource(R.string.house_equity, house.loanName, house.equityText.figure()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun HouseEditRoute(
    accountId: Long?,
    onDone: () -> Unit,
    viewModel: HouseEditViewModel = viewModel(factory = HouseEditViewModel.factory(accountId)),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    HouseEditScreen(state, viewModel, onDone)
}

private enum class HouseDialog { PURCHASE_DATE, ADD_VALUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseEditScreen(state: HouseEditUiState, viewModel: HouseEditViewModel, onBack: () -> Unit) {
    val fields = viewModel.fields
    var dialog by rememberSaveable { mutableStateOf<HouseDialog?>(null) }
    var editingValueId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val required = stringResource(R.string.error_required)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.house_add else R.string.house_edit)) },
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
            if (state.isNew && state.convertible.isNotEmpty()) {
                DropdownField(
                    label = stringResource(R.string.house_from_account),
                    options = listOf(DropdownOption<Long?>(null, stringResource(R.string.house_from_account_none))) +
                        state.convertible.map { DropdownOption<Long?>(it.id, "${it.name} · ${it.currencyCode}") },
                    selected = state.convertFrom,
                    onSelect = viewModel::onConvert,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                state = fields.name,
                label = { Text(stringResource(R.string.house_name_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(required) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.account_currency_label),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = state.currencyCode,
                onSelect = viewModel::onCurrencyChange,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.account_country_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.country_general))) +
                    state.countries.map { DropdownOption<Long?>(it.id, it.name) },
                selected = state.countryId,
                onSelect = viewModel::onCountryChange,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = fields.price,
                label = { Text(stringResource(R.string.house_price_label)) },
                suffix = { Text(state.currencyCode) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.priceError,
                supportingText = if (state.priceError) ({ Text(stringResource(R.string.recurring_amount_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(stringResource(R.string.house_date_label), state.purchaseDate, dateFormat, onClick = { dialog = HouseDialog.PURCHASE_DATE })
            OutlinedTextField(
                state = fields.growth,
                label = { Text(stringResource(R.string.house_growth_label)) },
                suffix = { Text("%") },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.growthError,
                supportingText = { Text(stringResource(if (state.growthError) R.string.house_growth_error else R.string.house_growth_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.house_loan_label),
                options = listOf(DropdownOption<Long?>(null, stringResource(R.string.house_loan_none))) +
                    state.loans.map { DropdownOption<Long?>(it.id, "${it.name} · ${it.currencyCode}") },
                selected = state.loanAccountId,
                onSelect = viewModel::onLoanChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }

            if (!state.isNew) {
                Text(stringResource(R.string.house_values_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.house_values_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.valuations.forEach { value ->
                        ListItem(
                            headlineContent = { Text(value.amountText) },
                            supportingContent = {
                                Text(
                                    if (value.isPurchase) {
                                        stringResource(R.string.house_value_purchase, value.date.format(dateFormat))
                                    } else {
                                        value.date.format(dateFormat)
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            // The purchase is changed with the fields above.
                            modifier = if (value.isPurchase) Modifier else Modifier.clickable { editingValueId = value.id },
                        )
                    }
                }
                OutlinedButton(onClick = { dialog = HouseDialog.ADD_VALUE }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.house_add_value))
                }
            }
        }
    }

    val rates = RateSupport(state.currencyCode, state.baseCurrency, state.rateBook, viewModel.rateLookups)
    when (dialog) {
        HouseDialog.PURCHASE_DATE -> BalanceDatePicker(
            initial = state.purchaseDate,
            onPick = { viewModel.onPurchaseDateChange(it); dialog = null },
            onDismiss = { dialog = null },
        )
        HouseDialog.ADD_VALUE -> BalanceEntryDialog(
            title = stringResource(R.string.house_add_value),
            accountName = null,
            currencyCode = state.currencyCode,
            decimals = state.decimals,
            initialAmountText = "",
            initialDate = LocalDate.now(),
            isLiability = false,
            rates = rates,
            onSave = { date, minor, rate, _, _ -> viewModel.addValuation(date, minor, rate); dialog = null },
            onDelete = null,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    editingValueId?.let { id ->
        val value = state.valuations.firstOrNull { it.id == id }
        if (value == null) {
            editingValueId = null
        } else {
            BalanceEntryDialog(
                title = stringResource(R.string.house_edit_value),
                accountName = null,
                currencyCode = state.currencyCode,
                decimals = state.decimals,
                initialAmountText = minorToInputText(value.amountMinor, state.decimals),
                initialDate = value.date,
                isLiability = false,
                rates = rates,
                onSave = { date, minor, rate, _, _ -> viewModel.editValuation(id, date, minor, rate); editingValueId = null },
                onDelete = { viewModel.deleteValuation(id); editingValueId = null },
                onDismiss = { editingValueId = null },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = fields.name.text.toString(),
            message = stringResource(R.string.house_delete_message),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
