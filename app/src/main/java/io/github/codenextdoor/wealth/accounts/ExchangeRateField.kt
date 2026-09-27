package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.formatRate
import io.github.codenextdoor.wealth.domain.parsePositiveDecimal
import java.math.BigDecimal

/** A rate the user typed with a balance, to be saved for the balance's date. */
data class RateEntry(val from: String, val to: String, val rate: BigDecimal)

/**
 * The exchange rate shown next to a balance, between the account's
 * [currency] and the [base] currency. It reads whichever way gives a number
 * of at least 1 ("1 CHF = 105 INR" rather than "1 INR = 0.0095 CHF").
 */
class RateFieldModel(
    private val currency: String,
    private val base: String,
    /** Units of [base] per 1 [currency] on the balance's date, if known. */
    private val known: BigDecimal?,
) {
    private val baseFirst = known != null && known < BigDecimal.ONE
    val from: String get() = if (baseFirst) base else currency
    val to: String get() = if (baseFirst) currency else base

    /** The known rate in display direction, as text; empty if unknown. */
    val defaultText: String = known?.let {
        formatRate(if (baseFirst) BigDecimal.ONE.divide(it, CurrencyConverter.MATH) else it)
    }.orEmpty()

    /**
     * What to save: null when the user kept the known rate. Returns
     * [Result.failure] when their text isn't a valid rate.
     */
    fun entryFor(text: String, edited: Boolean): Result<RateEntry?> {
        if (!edited || text.trim() == defaultText) return Result.success(null)
        val value = parsePositiveDecimal(text) ?: return Result.failure(IllegalArgumentException("invalid rate"))
        return Result.success(RateEntry(from, to, value))
    }
}

@Composable
fun ExchangeRateField(
    model: RateFieldModel,
    state: TextFieldState,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.rate_on_date_label, model.from, model.to)) },
        suffix = { Text(model.to) },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = isError,
        supportingText = {
            Text(stringResource(if (isError) R.string.rate_error_invalid else R.string.rate_on_date_hint))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
