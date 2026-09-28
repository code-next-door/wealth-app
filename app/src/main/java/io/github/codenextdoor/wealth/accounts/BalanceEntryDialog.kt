package io.github.codenextdoor.wealth.accounts

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import io.github.codenextdoor.wealth.domain.parseNonNegativeDecimal
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Add or edit one balance history entry: amount and date (and for accounts
 * holding shares, the number of shares, with the amount being the cash).
 * [onSave] receives the amount in minor units. [onDelete] is null when deleting isn't allowed
 * (e.g. the account's only entry).
 */
@Composable
fun BalanceEntryDialog(
    title: String,
    accountName: String?,
    currencyCode: String,
    decimals: Int,
    initialAmountText: String,
    initialDate: LocalDate,
    isLiability: Boolean,
    /** Saved and downloaded rates for [currencyCode]; the rate field shows only when it isn't the base currency. */
    rates: RateSupport,
    onSave: (date: LocalDate, balanceMinor: Long, rate: RateEntry?, units: BigDecimal?, price: PriceEntry?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Set for accounts holding shares. */
    shares: SharesSupport? = null,
    initialUnitsText: String = "",
) {
    val amount = rememberTextFieldState(initialAmountText)
    val unitsField = rememberTextFieldState(initialUnitsText)
    val priceField = rememberTextFieldState()
    var shownPriceDefault by rememberSaveable { mutableStateOf("") }
    val units = unitsField.text.toString().trim().takeIf { it.isNotEmpty() }?.let { parseNonNegativeDecimal(it) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val rateField = rememberTextFieldState()
    // The saved rate currently shown; the field follows it until the user types their own.
    var shownRateDefault by rememberSaveable { mutableStateOf("") }
    val parsed = if (shares != null && amount.text.isBlank()) 0L else parseAmountToMinor(amount.text.toString(), decimals)
    val needsRate = rates.needed
    // Picking a date downloads that day's rate; it fills the field once saved.
    LaunchedEffect(date) { rates.request(date) }
    val rateModel = rates.model(date)
    LaunchedEffect(rateModel.defaultText) {
        if (rateField.text.toString() == shownRateDefault) rateField.setTextAndPlaceCursorAtEnd(rateModel.defaultText)
        shownRateDefault = rateModel.defaultText
    }
    val rateText = rateField.text.toString()
    val rateResult = rateModel.entryFor(rateText, edited = rateText != shownRateDefault)
    val priceModel = shares?.model(date)
    if (shares != null && priceModel != null) {
        LaunchedEffect(date) { shares.request(date) }
        LaunchedEffect(priceModel.defaultText) {
            if (priceField.text.toString() == shownPriceDefault) priceField.setTextAndPlaceCursorAtEnd(priceModel.defaultText)
            shownPriceDefault = priceModel.defaultText
        }
    }
    val priceText = priceField.text.toString()
    val priceResult = priceModel?.entryFor(priceText, edited = priceText != shownPriceDefault) ?: Result.success(null)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (accountName != null) {
                    Text(accountName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (shares != null) {
                    OutlinedTextField(
                        state = unitsField,
                        label = { Text(stringResource(R.string.account_units_label)) },
                        suffix = { Text(shares.symbol) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        isError = showErrors && units == null,
                        supportingText = if (showErrors && units == null) ({ Text(stringResource(R.string.account_units_error)) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(
                            stringResource(
                                when {
                                    shares != null -> R.string.account_cash_label
                                    isLiability -> R.string.account_owed_label
                                    else -> R.string.account_balance_label
                                },
                            ),
                        )
                    },
                    suffix = { Text(currencyCode) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = showErrors && parsed == null,
                    supportingText = if (showErrors && parsed == null) {
                        {
                            Text(
                                if (amount.text.isBlank()) {
                                    stringResource(R.string.error_required)
                                } else {
                                    pluralStringResource(R.plurals.account_balance_invalid, decimals, decimals)
                                },
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.balance_entry_date),
                    date = date,
                    format = dateFormat,
                    onClick = { pickDate = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (shares != null && priceModel != null) {
                    SharePriceField(
                        model = priceModel,
                        state = priceField,
                        isError = showErrors && priceResult.isFailure,
                        date = date,
                        status = shares.status(date),
                        saved = shares.saved(date),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                if (needsRate) {
                    ExchangeRateField(
                        model = rateModel,
                        state = rateField,
                        isError = showErrors && rateResult.isFailure,
                        date = date,
                        status = rates.status(date),
                        saved = rates.saved(date),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                val sharesOk = shares == null || (units != null && priceResult.isSuccess)
                if (parsed != null && rateResult.isSuccess && sharesOk) {
                    onSave(date, parsed, rateResult.getOrNull(), units.takeIf { shares != null }, priceResult.getOrNull())
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )

    if (pickDate) {
        BalanceDatePicker(
            initial = date,
            onPick = { date = it; pickDate = false },
            onDismiss = { pickDate = false },
        )
    }
}

/** Read-only field showing a date; tapping anywhere on it calls [onClick]. */
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    format: DateTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = date.format(format),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The overlay catches taps (a read-only field ignores them) and carries
        // the label and value for screen readers.
        val description = "$label: ${date.format(format)}"
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
        )
    }
}

/** Material date picker limited to today and earlier, unless [allowFuture]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, allowFuture: Boolean = false) {
    // The Material date picker works in UTC midnight milliseconds.
    val todayMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= todayMillis
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
