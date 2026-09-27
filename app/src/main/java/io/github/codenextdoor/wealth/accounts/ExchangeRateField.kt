package io.github.codenextdoor.wealth.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.RatePoint
import io.github.codenextdoor.wealth.domain.formatRate
import io.github.codenextdoor.wealth.domain.parsePositiveDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A rate chosen with a balance, to be saved for the balance's date: typed by
 * the user, or the downloaded one picked with "Use downloaded rate" ([fetched]).
 */
data class RateEntry(val from: String, val to: String, val rate: BigDecimal, val fetched: Boolean = false)

/** Where downloading a day's rate has got to. */
sealed interface RateStatus {
    data object Idle : RateStatus
    data object Loading : RateStatus

    /** [rate]: units of the base currency per 1 unit; [date]: the day it's from. */
    data class Found(val rate: BigDecimal, val date: LocalDate) : RateStatus
    data object Unavailable : RateStatus
}

/**
 * Downloads rates for one screen's forms. Results are Compose state, so a
 * field updates as soon as its rate arrives.
 */
class RateLookups(private val updater: RateUpdater, private val scope: CoroutineScope) {
    private val results = mutableStateMapOf<Pair<String, LocalDate>, RateStatus>()

    fun statusFor(currency: String, date: LocalDate): RateStatus = results[currency to date] ?: RateStatus.Idle

    /** Downloads (and saves) [currency]'s rate for [date], unless already done; a failed one is retried. */
    fun request(currency: String, date: LocalDate) {
        val key = currency to date
        if (results[key] == RateStatus.Loading || results[key] is RateStatus.Found) return
        results[key] = RateStatus.Loading
        scope.launch {
            results[key] = updater.lookUp(currency, date)?.let { RateStatus.Found(it.rate, it.date) } ?: RateStatus.Unavailable
        }
    }
}

/** What a rate field needs for balances in [currency]: the saved rates and the downloads. */
class RateSupport(
    val currency: String,
    val base: String,
    private val book: RateBook,
    private val lookups: RateLookups?,
) {
    /** False for balances in the base currency: no rate to show. */
    val needed: Boolean get() = base.isNotEmpty() && currency != base

    fun status(date: LocalDate): RateStatus = lookups?.statusFor(currency, date) ?: RateStatus.Idle

    fun request(date: LocalDate) {
        if (needed) lookups?.request(currency, date)
    }

    /** The saved rate in effect on [date]: its day and whether it was downloaded. */
    fun saved(date: LocalDate): RatePoint? = book.pointAt(currency, base, date)

    fun model(date: LocalDate) =
        RateFieldModel(currency, base, book.converterAt(date).rate(currency, base), (status(date) as? RateStatus.Found)?.rate)
}

/**
 * The exchange rate shown next to a balance, between the account's
 * [currency] and the [base] currency. It reads whichever way gives a number
 * of at least 1 ("1 CHF = 105 INR" rather than "1 INR = 0.0095 CHF").
 */
class RateFieldModel(
    private val currency: String,
    private val base: String,
    /** Units of [base] per 1 [currency] saved for the balance's date, if any. */
    private val known: BigDecimal?,
    /** The same, as just downloaded for that date, if it was. */
    private val fetched: BigDecimal? = null,
) {
    private val baseFirst = (known ?: fetched)?.let { it < BigDecimal.ONE } == true
    val from: String get() = if (baseFirst) base else currency
    val to: String get() = if (baseFirst) currency else base

    private fun inDisplayDirection(rate: BigDecimal) = if (baseFirst) BigDecimal.ONE.divide(rate, CurrencyConverter.MATH) else rate

    /** The saved rate in display direction, as text; empty if unknown. */
    val defaultText: String = known?.let { formatRate(inDisplayDirection(it)) }.orEmpty()

    /** The downloaded rate in display direction, as text; null if none. */
    val fetchedText: String? = fetched?.let { formatRate(inDisplayDirection(it)) }

    /**
     * What to save: null when the user kept the saved rate. The downloaded
     * rate is saved at full precision and marked as downloaded. Returns
     * [Result.failure] when the text isn't a valid rate.
     */
    fun entryFor(text: String, edited: Boolean): Result<RateEntry?> {
        val typed = text.trim()
        if (!edited || typed == defaultText) return Result.success(null)
        if (fetched != null && typed == fetchedText) return Result.success(RateEntry(from, to, inDisplayDirection(fetched), fetched = true))
        val value = parsePositiveDecimal(typed) ?: return Result.failure(IllegalArgumentException("invalid rate"))
        return Result.success(RateEntry(from, to, value))
    }
}

/**
 * The rate field with a line saying where the rate comes from (downloaded,
 * typed, still loading, not available) and, when a downloaded rate differs
 * from what's in the field, a button to use it.
 */
@Composable
fun ExchangeRateField(
    model: RateFieldModel,
    state: TextFieldState,
    isError: Boolean,
    date: LocalDate,
    status: RateStatus,
    saved: RatePoint?,
    modifier: Modifier = Modifier,
) {
    val text = state.text.toString().trim()
    val edited = text != model.defaultText
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val savedIsNearby = saved != null && !saved.date.isAfter(date) && !saved.date.isBefore(date.minusDays(6))
    val hint = when {
        isError -> stringResource(R.string.rate_error_invalid)
        edited && text == model.fetchedText -> stringResource(R.string.rate_hint_use_downloaded)
        edited -> stringResource(R.string.rate_hint_override)
        status == RateStatus.Loading -> stringResource(R.string.rate_hint_loading)
        status == RateStatus.Unavailable && saved == null -> stringResource(R.string.rate_hint_unavailable_none)
        status == RateStatus.Unavailable && !savedIsNearby -> stringResource(R.string.rate_hint_unavailable)
        saved != null && saved.fetched -> stringResource(R.string.rate_hint_downloaded, saved.date.format(format))
        saved != null -> stringResource(R.string.rate_hint_typed, saved.date.format(format))
        else -> stringResource(R.string.rate_on_date_hint)
    }
    Column(modifier) {
        OutlinedTextField(
            state = state,
            label = { Text(stringResource(R.string.rate_on_date_label, model.from, model.to)) },
            suffix = { Text(model.to) },
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
            supportingText = { Text(hint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        val fetchedText = model.fetchedText
        if (fetchedText != null && fetchedText != text) {
            TextButton(onClick = { state.setTextAndPlaceCursorAtEnd(fetchedText) }) {
                Text(stringResource(R.string.rate_use_downloaded, fetchedText, model.to))
            }
        }
    }
}
