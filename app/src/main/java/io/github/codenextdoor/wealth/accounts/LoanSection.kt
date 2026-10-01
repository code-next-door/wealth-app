package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.formatPercent
import io.github.codenextdoor.wealth.domain.minorToDecimal
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What the loan part of the account form can ask for (no-ops in previews). */
data class LoanActions(
    val onCalculateChange: (Boolean) -> Unit = {},
    val onFirstEmiChange: (LocalDate) -> Unit = {},
    val onEmiCategoryChange: (Long?) -> Unit = {},
    /** Returns false when the rate or EMI typed isn't valid. */
    val onAddRateChange: (from: LocalDate, rateText: String, emiText: String) -> Boolean = { _, _, _ -> false },
    val onRemoveRateChange: (LocalDate) -> Unit = {},
)

/**
 * For loan types: a switch to calculate the outstanding from the loan's terms
 * (principal, first EMI, EMI, rate and later rate changes) instead of typing it.
 */
@Composable
fun LoanSection(state: AccountEditUiState, fields: AccountTextFields, actions: LoanActions, dateFormat: DateTimeFormatter) {
    val form = state.form
    val required = stringResource(R.string.error_required)
    val currency = state.selectedCurrency
    var pickFirstEmi by rememberSaveable { mutableStateOf(false) }
    var addingChange by rememberSaveable { mutableStateOf(false) }

    // The whole row toggles (one target for touch and screen readers).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = form.calculateLoan, role = Role.Switch, onValueChange = actions.onCalculateChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.loan_calculate), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.loan_calculate_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = form.calculateLoan, onCheckedChange = null)
    }
    if (!form.calculateLoan) return

    OutlinedTextField(
        state = fields.loanPrincipal,
        label = { Text(stringResource(R.string.loan_principal)) },
        suffix = { form.currencyCode?.let { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = state.loanPrincipalError,
        supportingText = if (state.loanPrincipalError) ({ Text(required) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    DateField(label = stringResource(R.string.loan_first_emi), date = form.loanFirstEmi, format = dateFormat, onClick = { pickFirstEmi = true })
    OutlinedTextField(
        state = fields.loanEmi,
        label = { Text(stringResource(R.string.loan_emi)) },
        suffix = { form.currencyCode?.let { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = state.loanEmiError,
        supportingText = if (state.loanEmiError) ({ Text(required) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state = fields.loanRate,
        label = { Text(stringResource(R.string.loan_rate)) },
        suffix = { Text("%") },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = state.loanRateError,
        supportingText = if (state.loanRateError) ({ Text(required) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    DropdownField(
        label = stringResource(R.string.loan_emi_category),
        options = listOf(DropdownOption<Long?>(null, stringResource(R.string.loan_emi_category_none))) +
            state.categories.map { DropdownOption<Long?>(it.id, it.name) },
        selected = form.loanEmiCategoryId,
        onSelect = actions.onEmiCategoryChange,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.loan_emi_category_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Rate changes: from that day the rate (and maybe the EMI) is different; otherwise the last one continues.
    Text(stringResource(R.string.loan_rate_changes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    if (form.loanRateChanges.isEmpty()) {
        Text(stringResource(R.string.loan_rate_changes_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    form.loanRateChanges.forEach { change ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val rate = formatPercent(change.yearlyRate, decimals = 2)
            val emi = change.emiMinor?.let { formatMoney(minorToDecimal(it, currency?.decimals ?: 2), form.currencyCode.orEmpty(), currency?.decimals ?: 2) }
            Text(
                listOfNotNull(change.from.format(dateFormat), rate, emi?.let { stringResource(R.string.loan_new_emi, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.onRemoveRateChange(change.from) }) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.loan_remove_rate_change, change.from.format(dateFormat)))
            }
        }
    }
    TextButton(onClick = { addingChange = true }) { Text(stringResource(R.string.loan_add_rate_change)) }

    state.loanOutstandingToday?.let { outstanding ->
        val dec = currency?.decimals ?: 2
        Text(
            stringResource(R.string.loan_outstanding_today, formatMoney(minorToDecimal(outstanding, dec), form.currencyCode.orEmpty(), dec)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (pickFirstEmi) {
        BalanceDatePicker(initial = form.loanFirstEmi, onPick = { actions.onFirstEmiChange(it); pickFirstEmi = false }, onDismiss = { pickFirstEmi = false }, allowFuture = true)
    }
    if (addingChange) {
        RateChangeDialog(dateFormat, onAdd = actions.onAddRateChange, onDone = { addingChange = false })
    }
}

/** A new rate from a day, and optionally a new EMI. */
@Composable
private fun RateChangeDialog(dateFormat: DateTimeFormatter, onAdd: (LocalDate, String, String) -> Boolean, onDone: () -> Unit) {
    var from by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val rate = rememberTextFieldState()
    val emi = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.loan_add_rate_change)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = stringResource(R.string.loan_rate_change_from), date = from, format = dateFormat, onClick = { picking = true })
                OutlinedTextField(
                    state = rate,
                    label = { Text(stringResource(R.string.loan_rate)) },
                    suffix = { Text("%") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.loan_rate_change_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state = emi,
                    label = { Text(stringResource(R.string.loan_new_emi_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onAdd(from, rate.text.toString(), emi.text.toString())) onDone() else invalid = true }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) BalanceDatePicker(initial = from, onPick = { from = it; picking = false }, onDismiss = { picking = false }, allowFuture = true)
}
