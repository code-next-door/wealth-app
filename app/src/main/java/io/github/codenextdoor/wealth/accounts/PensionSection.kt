package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.minorToDecimal

/**
 * For types that grow with contributions (pillar 2, EPF, PPF): a switch to let the value
 * grow between statements from the yearly contributions and interest (see PensionValue).
 */
@Composable
fun PensionSection(state: AccountEditUiState, fields: AccountTextFields, onCalculateChange: (Boolean) -> Unit) {
    val form = state.form
    val required = stringResource(R.string.error_required)
    // The whole row toggles (one target for touch and screen readers).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = form.calculatePension, role = Role.Switch, onValueChange = onCalculateChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pension_calculate), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.pension_calculate_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = form.calculatePension, onCheckedChange = null)
    }
    if (!form.calculatePension) return

    OutlinedTextField(
        state = fields.pensionContribution,
        label = { Text(stringResource(R.string.pension_contribution)) },
        suffix = { form.currencyCode?.let { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = state.pensionContributionError,
        supportingText = { Text(if (state.pensionContributionError) required else stringResource(R.string.pension_contribution_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state = fields.pensionRate,
        label = { Text(stringResource(R.string.pension_rate)) },
        suffix = { Text("%") },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = state.pensionRateError,
        supportingText = if (state.pensionRateError) ({ Text(required) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    val currency = state.selectedCurrency
    val today = state.pensionValueToday
    if (currency != null && today != null) {
        Text(
            stringResource(R.string.pension_value_today, formatMoney(minorToDecimal(today, currency.decimals), currency.code, currency.decimals)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
