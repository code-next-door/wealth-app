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
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.PricePoint
import androidx.annotation.StringRes
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
 * Downloads rates (or share prices) for one screen's forms. Results are
 * Compose state, so a field updates as soon as its value arrives.
 */
class RateLookups(
    private val scope: CoroutineScope,
    /** Downloads and saves the value for a currency (or share symbol) and day. */
    private val lookUp: suspend (String, LocalDate) -> RateStatus.Found?,
) {
    constructor(updater: RateUpdater, scope: CoroutineScope) :
        this(scope, { currency, date -> updater.lookUp(currency, date)?.let { RateStatus.Found(it.rate, it.date) } })

    private val results = mutableStateMapOf<Pair<String, LocalDate>, RateStatus>()

    fun statusFor(key: String, date: LocalDate): RateStatus = results[key to date] ?: RateStatus.Idle

    /** Downloads (and saves) the value for [key] on [date], unless already done; a failed one is retried. */
    fun request(key: String, date: LocalDate) {
        val entry = key to date
        if (results[entry] == RateStatus.Loading || results[entry] is RateStatus.Found) return
        results[entry] = RateStatus.Loading
        scope.launch { results[entry] = lookUp(key, date) ?: RateStatus.Unavailable }
    }

    companion object {
        fun forPrices(updater: PriceUpdater, scope: CoroutineScope) =
            RateLookups(scope) { symbol, date -> updater.lookUp(symbol, date)?.let { RateStatus.Found(it.price, it.date) } }
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
) = DownloadableValueField(
    state = state,
    label = stringResource(R.string.rate_on_date_label, model.from, model.to),
    suffix = model.to,
    defaultText = model.defaultText,
    fetchedText = model.fetchedText,
    isError = isError,
    date = date,
    status = status,
    savedDate = saved?.date,
    savedFetched = saved?.fetched == true,
    texts = RATE_TEXTS,
    modifier = modifier,
)

/** What a balance dialog needs for an account holding [symbol] shares: saved and downloaded prices. */
class SharesSupport(
    val symbol: String,
    val currency: String,
    private val book: PriceBook,
    private val lookups: RateLookups?,
) {
    fun status(date: LocalDate): RateStatus = lookups?.statusFor(symbol, date) ?: RateStatus.Idle

    fun request(date: LocalDate) {
        lookups?.request(symbol, date)
    }

    fun saved(date: LocalDate): PricePoint? = book.pointAt(symbol, date)

    fun model(date: LocalDate) = PriceFieldModel(symbol, currency, book.priceAt(symbol, date), (status(date) as? RateStatus.Found)?.rate)
}

/** A share price chosen with a balance: typed, or the downloaded one picked with "Use downloaded price". */
data class PriceEntry(val symbol: String, val price: BigDecimal, val fetched: Boolean = false)

/** The price of one [symbol] share on a balance's date, in [currency]. */
class PriceFieldModel(
    val symbol: String,
    val currency: String,
    /** The saved price for that day (or the last one before), if any. */
    known: BigDecimal?,
    /** The same, as just downloaded, if it was. */
    private val fetched: BigDecimal? = null,
) {
    val defaultText: String = known?.let(::display).orEmpty()
    val fetchedText: String? = fetched?.let(::display)

    /** Two to four decimals: "156.23", "0.1234". */
    private fun display(price: BigDecimal): String =
        price.setScale(price.stripTrailingZeros().scale().coerceIn(2, 4), java.math.RoundingMode.HALF_EVEN).toPlainString()

    /** Like [RateFieldModel.entryFor]: null keeps the saved price. */
    fun entryFor(text: String, edited: Boolean): Result<PriceEntry?> {
        val typed = text.trim()
        if (!edited || typed == defaultText) return Result.success(null)
        if (fetched != null && typed == fetchedText) return Result.success(PriceEntry(symbol, fetched, fetched = true))
        val value = parsePositiveDecimal(typed) ?: return Result.failure(IllegalArgumentException("invalid price"))
        return Result.success(PriceEntry(symbol, value))
    }
}

/** The share price field; works like [ExchangeRateField]. */
@Composable
fun SharePriceField(
    model: PriceFieldModel,
    state: TextFieldState,
    isError: Boolean,
    date: LocalDate,
    status: RateStatus,
    saved: PricePoint?,
    modifier: Modifier = Modifier,
) = DownloadableValueField(
    state = state,
    label = stringResource(R.string.price_on_date_label, model.symbol),
    suffix = model.currency,
    defaultText = model.defaultText,
    fetchedText = model.fetchedText,
    isError = isError,
    date = date,
    status = status,
    savedDate = saved?.date,
    savedFetched = saved?.fetched == true,
    texts = PRICE_TEXTS,
    modifier = modifier,
)

/** The texts that differ between the rate and the price field. */
private class HintTexts(
    @StringRes val downloaded: Int,
    @StringRes val typed: Int,
    @StringRes val unavailable: Int,
    @StringRes val unavailableNone: Int,
    @StringRes val override: Int,
    @StringRes val useDownloadedHint: Int,
    @StringRes val useDownloaded: Int,
    @StringRes val fallback: Int,
)

private val RATE_TEXTS = HintTexts(
    R.string.rate_hint_downloaded, R.string.rate_hint_typed, R.string.rate_hint_unavailable, R.string.rate_hint_unavailable_none,
    R.string.rate_hint_override, R.string.rate_hint_use_downloaded, R.string.rate_use_downloaded, R.string.rate_on_date_hint,
)

private val PRICE_TEXTS = HintTexts(
    R.string.price_hint_downloaded, R.string.price_hint_typed, R.string.price_hint_unavailable, R.string.price_hint_unavailable_none,
    R.string.price_hint_override, R.string.price_hint_use_downloaded, R.string.price_use_downloaded, R.string.price_on_date_hint,
)

/**
 * A value that's downloaded for a day but can be typed over: the field, a
 * line saying where the value comes from, and a button to go back to the
 * downloaded value.
 */
@Composable
private fun DownloadableValueField(
    state: TextFieldState,
    label: String,
    suffix: String,
    defaultText: String,
    fetchedText: String?,
    isError: Boolean,
    date: LocalDate,
    status: RateStatus,
    savedDate: LocalDate?,
    savedFetched: Boolean,
    texts: HintTexts,
    modifier: Modifier,
) {
    val text = state.text.toString().trim()
    val edited = text != defaultText
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val savedIsNearby = savedDate != null && !savedDate.isAfter(date) && !savedDate.isBefore(date.minusDays(6))
    val hint = when {
        isError -> stringResource(R.string.rate_error_invalid)
        edited && text == fetchedText -> stringResource(texts.useDownloadedHint)
        edited -> stringResource(texts.override)
        status == RateStatus.Loading -> stringResource(R.string.rate_hint_loading)
        status == RateStatus.Unavailable && savedDate == null -> stringResource(texts.unavailableNone)
        status == RateStatus.Unavailable && !savedIsNearby -> stringResource(texts.unavailable)
        savedDate != null && savedFetched -> stringResource(texts.downloaded, savedDate.format(format))
        savedDate != null -> stringResource(texts.typed, savedDate.format(format))
        else -> stringResource(texts.fallback)
    }
    Column(modifier) {
        OutlinedTextField(
            state = state,
            label = { Text(label) },
            suffix = { Text(suffix) },
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
            supportingText = { Text(hint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (fetchedText != null && fetchedText != text) {
            TextButton(onClick = { state.setTextAndPlaceCursorAtEnd(fetchedText) }) {
                Text(stringResource(texts.useDownloaded, fetchedText, suffix))
            }
        }
    }
}
