package io.github.codenextdoor.wealth.accounts

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.parseAmountToMinor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Add or edit one balance history entry: amount and date. [onSave] receives
 * the amount in minor units. [onDelete] is null when deleting isn't allowed
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
    /** Base currency; the rate field shows only when it differs from [currencyCode]. */
    baseCurrency: String,
    /** Units of base currency per 1 [currencyCode] known for a date, if any. */
    rateOn: (LocalDate) -> BigDecimal?,
    onSave: (date: LocalDate, balanceMinor: Long, rate: RateEntry?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(initialAmountText) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var rateText by rememberSaveable { mutableStateOf<String?>(null) } // null = follow the known rate
    val parsed = parseAmountToMinor(amount, decimals)
    val needsRate = currencyCode != baseCurrency
    val rateModel = RateFieldModel(currencyCode, baseCurrency, rateOn(date))
    val rateResult = rateModel.entryFor(rateText ?: rateModel.defaultText, edited = rateText != null)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (accountName != null) {
                    Text(accountName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(if (isLiability) R.string.account_owed_label else R.string.account_balance_label)) },
                    suffix = { Text(currencyCode) },
                    singleLine = true,
                    isError = showErrors && parsed == null,
                    supportingText = if (showErrors && parsed == null) {
                        {
                            Text(
                                if (amount.isBlank()) {
                                    stringResource(R.string.error_required)
                                } else {
                                    stringResource(R.string.account_balance_invalid, decimals)
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
                if (needsRate) {
                    ExchangeRateField(
                        model = rateModel,
                        text = rateText ?: rateModel.defaultText,
                        onTextChange = { rateText = it },
                        isError = showErrors && rateResult.isFailure,
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
                val rate = rateResult.getOrNull()
                if (parsed != null && rateResult.isSuccess) onSave(date, parsed, rate)
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

/** Material date picker limited to today and earlier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
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
