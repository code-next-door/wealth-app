package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.domain.minorToInputText
import io.github.codenextdoor.wealth.ui.components.SectionHeader
import java.math.BigDecimal
import java.time.LocalDate
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
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }
    AccountEditScreen(
        state = state,
        onBack = onDone,
        fields = viewModel.fields,
        rateLookups = viewModel.rateLookups,
        priceLookups = viewModel.priceLookups,
        onRateDefaultShown = viewModel::showRateDefault,
        onPriceDefaultShown = viewModel::showPriceDefault,
        onTypeChange = viewModel::onTypeChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onCountryChange = viewModel::onCountryChange,
        onBalanceDateChange = viewModel::onBalanceDateChange,
        onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
        onEditHistoryEntry = viewModel::editHistoryEntry,
        onAddHistoryEntry = viewModel::addHistoryEntry,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    state: AccountEditUiState,
    onBack: () -> Unit,
    fields: AccountTextFields,
    /** Downloads rates; null in previews. */
    rateLookups: RateLookups?,
    priceLookups: RateLookups?,
    onRateDefaultShown: (String) -> Unit,
    onPriceDefaultShown: (String) -> Unit,
    onTypeChange: (Long) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onCountryChange: (Long?) -> Unit,
    onBalanceDateChange: (LocalDate) -> Unit,
    onDeleteHistoryEntry: (entryId: Long) -> Unit,
    onEditHistoryEntry: (entryId: Long, date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal?, price: PriceEntry?) -> Unit,
    onAddHistoryEntry: (date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal?, price: PriceEntry?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var deleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingEntry by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val form = state.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.account_add else R.string.account_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
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
            val required = stringResource(R.string.error_required)

            OutlinedTextField(
                state = fields.name,
                label = { Text(stringResource(R.string.account_name_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
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

            if (state.holdsShares) {
                OutlinedTextField(
                    state = fields.symbol,
                    label = { Text(stringResource(R.string.grant_symbol_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = state.symbolError,
                    supportingText = if (state.symbolError) ({ Text(required) }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state = fields.units,
                    label = { Text(stringResource(R.string.account_units_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = state.unitsError,
                    supportingText = if (state.unitsError) ({ Text(stringResource(R.string.account_units_error)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                state = fields.balance,
                label = {
                    Text(
                        stringResource(
                            when {
                                state.holdsShares -> R.string.account_cash_label
                                state.isLiability -> R.string.account_owed_label
                                else -> R.string.account_balance_label
                            },
                        ),
                    )
                },
                suffix = { form.currencyCode?.let { Text(it) } },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = state.balanceError,
                supportingText = when {
                    !state.balanceError -> null
                    form.balanceText.isBlank() -> ({ Text(required) })
                    else -> ({ Text((state.selectedCurrency?.decimals ?: 2).let { pluralStringResource(R.plurals.account_balance_invalid, it, it) }) })
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = stringResource(R.string.account_balance_date_label),
                date = form.balanceDate,
                format = dateFormat,
                onClick = { pickDate = true },
            )

            state.priceModel?.let { model ->
                // Like the rate: follows the saved price for the day, and picking a date downloads it.
                LaunchedEffect(model.defaultText) { onPriceDefaultShown(model.defaultText) }
                LaunchedEffect(model.symbol, form.balanceDate) { priceLookups?.request(model.symbol, form.balanceDate) }
                SharePriceField(
                    model = model,
                    state = fields.price,
                    isError = state.priceError,
                    date = form.balanceDate,
                    status = state.priceStatus,
                    saved = state.savedPrice,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.rateModel?.let { model ->
                // Keep the field on the saved rate for this date until the user types their own.
                LaunchedEffect(model.defaultText) { onRateDefaultShown(model.defaultText) }
                // Picking a currency or date downloads that day's rate; once saved it becomes the field's value.
                val currencyCode = state.selectedCurrency?.code
                LaunchedEffect(currencyCode, form.balanceDate) {
                    if (currencyCode != null) rateLookups?.request(currencyCode, form.balanceDate)
                }
                ExchangeRateField(
                    model = model,
                    state = fields.rate,
                    isError = state.rateError,
                    date = form.balanceDate,
                    status = state.rateStatus,
                    saved = state.savedRate,
                    modifier = Modifier.fillMaxWidth(),
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
                state = fields.institution,
                label = { Text(stringResource(R.string.account_institution_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = fields.note,
                label = { Text(stringResource(R.string.account_note_label)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
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
                    onEdit = { editEntryId = it },
                    onAdd = { addingEntry = true },
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

    val currency = state.selectedCurrency
    val sharesSupport = state.shareSymbol.takeIf { state.holdsShares && it.isNotEmpty() }?.let {
        SharesSupport(it, currency?.code.orEmpty(), state.priceBook, priceLookups)
    }
    if (addingEntry && currency != null) {
        BalanceEntryDialog(
            title = stringResource(R.string.balance_entry_add),
            accountName = null,
            currencyCode = currency.code,
            decimals = currency.decimals,
            initialAmountText = "",
            initialDate = LocalDate.now().minusMonths(1),
            isLiability = state.isLiability,
            rates = RateSupport(currency.code, state.baseCurrency, state.rateBook, rateLookups),
            onSave = { date, minor, rate, units, price -> onAddHistoryEntry(date, minor, rate, units, price); addingEntry = false },
            onDelete = null,
            onDismiss = { addingEntry = false },
            shares = sharesSupport,
        )
    }

    editEntryId?.let { id ->
        val entry = state.history.firstOrNull { it.id == id }
        if (entry == null || currency == null) {
            editEntryId = null
        } else {
            BalanceEntryDialog(
                title = stringResource(R.string.balance_entry_edit),
                accountName = null,
                currencyCode = currency.code,
                decimals = currency.decimals,
                initialAmountText = minorToInputText(entry.balanceMinor, currency.decimals),
                initialDate = entry.date,
                isLiability = state.isLiability,
                rates = RateSupport(currency.code, state.baseCurrency, state.rateBook, rateLookups),
                onSave = { date, minor, rate, units, price -> onEditHistoryEntry(id, date, minor, rate, units, price); editEntryId = null },
                onDelete = if (state.history.size > 1) ({ editEntryId = null; deleteEntryId = id }) else null,
                onDismiss = { editEntryId = null },
                shares = sharesSupport,
                initialUnitsText = entry.units?.stripTrailingZeros()?.toPlainString().orEmpty(),
            )
        }
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
    onEdit: (entryId: Long) -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.account_history_title)) }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 16.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.balance_entry_add))
        }
    }
    Text(
        stringResource(R.string.account_history_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    entries.forEach { entry ->
        val decimals = currency?.decimals ?: 2
        ListItem(
            headlineContent = {
                Text(formatMoney(minorToDecimal(entry.balanceMinor, decimals), currency?.code ?: "", decimals))
            },
            supportingContent = { Text(entry.date.format(dateFormat)) },
            trailingContent = { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.balance_entry_edit)) },
            modifier = Modifier.clickable { onEdit(entry.id) },
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
            onBack = {}, fields = AccountTextFields(), rateLookups = null, priceLookups = null, onRateDefaultShown = {}, onPriceDefaultShown = {}, onTypeChange = {}, onCurrencyChange = {},
            onCountryChange = {}, onBalanceDateChange = {}, onDeleteHistoryEntry = {},
            onEditHistoryEntry = { _, _, _, _, _, _ -> }, onAddHistoryEntry = { _, _, _, _, _ -> },
            onSave = {}, onDelete = {},
        )
    }
}
