package io.github.codenextdoor.wealth.domain

import android.icu.number.LocalizedNumberFormatter
import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.icu.util.Currency as IcuCurrency

/**
 * Parses an amount typed by the user into minor units (cents, rappen, paise)
 * for a currency with [decimals] digits after the point. Zero and negative
 * amounts are allowed (e.g. an overdrawn account). Returns null if the text
 * isn't a number or has more decimals than the currency allows.
 */
fun parseAmountToMinor(input: String, decimals: Int): Long? {
    val value = normalizeNumberInput(input).toBigDecimalOrNull() ?: return null
    val exact = value.stripTrailingZeros()
    if (exact.scale() > decimals) return null
    return runCatching { value.movePointRight(decimals).longValueExact() }.getOrNull()
}

/** Minor units -> exact decimal amount, e.g. 123450 with 2 decimals -> 1234.50. */
fun minorToDecimal(minor: Long, decimals: Int): BigDecimal = BigDecimal.valueOf(minor, decimals)

/** Text to pre-fill an amount field with, e.g. "1234.5" (no grouping, no symbol). */
fun minorToInputText(minor: Long, decimals: Int): String =
    minorToDecimal(minor, decimals).stripTrailingZeros().toPlainString()

/**
 * Formats an amount in its currency's home style, whatever the phone's region:
 * CHF as in Switzerland ("CHF 1’234.50"), INR as in India ("₹12,34,567.00"),
 * USD as in the US ("$1,234.50"). Rounds half-even to the currency's decimals.
 */
fun formatMoney(
    amount: BigDecimal,
    currencyCode: String,
    decimals: Int,
    locale: Locale = Locale.getDefault(),
): String = moneyFormatter(currencyCode, locale)
    .precision(Precision.fixedFraction(decimals))
    .roundingMode(RoundingMode.HALF_EVEN)
    .format(amount)
    .toString()

/** A short form for chart axes in the currency's home style: "CHF 1.25M", "₹12.5L", "₹1.2Cr". */
fun formatMoneyShort(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String =
    moneyFormatter(currencyCode, locale)
        .notation(Notation.compactShort())
        .precision(Precision.maxSignificantDigits(3))
        .roundingMode(RoundingMode.HALF_EVEN)
        .format(amount)
        .toString()

/** A percentage in the phone's regional style, e.g. 3.25 -> "3.3%" (German: "3,3 %"). */
fun formatPercent(percent: BigDecimal, decimals: Int = 1, locale: Locale = Locale.getDefault()): String =
    NumberFormatter.withLocale(locale)
        .unit(MeasureUnit.PERCENT)
        .precision(Precision.fixedFraction(decimals))
        .roundingMode(RoundingMode.HALF_EVEN)
        .format(percent)
        .toString()

private fun moneyFormatter(currencyCode: String, locale: Locale): LocalizedNumberFormatter {
    val formatter = NumberFormatter.withLocale(moneyLocale(currencyCode, locale))
    // ICU takes any three-letter code; anything else (never saved by the app) shows as a plain number.
    return runCatching { formatter.unit(IcuCurrency.getInstance(currencyCode)) }.getOrDefault(formatter)
}

/**
 * The regional style for [currencyCode]: a currency code starts with its country's
 * code (CHF -> CH, INR -> IN), so the phone's language with that country's style,
 * or English with it if ICU has no data for that pair (German + India). Currencies
 * not tied to one country (EUR) keep the phone's own style.
 */
internal fun moneyLocale(currencyCode: String, device: Locale): Locale =
    moneyLocales.getOrPut(currencyCode to device) {
        val country = currencyCode.take(2).uppercase(Locale.ROOT)
        val home = runCatching { Locale.Builder().setRegion(country).build() }.getOrNull()
            ?.let { runCatching { java.util.Currency.getInstance(it).currencyCode }.getOrNull() }
        if (home != currencyCode) {
            device
        } else {
            listOf(device.language, "en")
                .map { Locale.Builder().setLanguage(it).setRegion(country).build() }
                .firstOrNull { it.toLanguageTag() in icuLocales }
                ?: device
        }
    }

private val moneyLocales = ConcurrentHashMap<Pair<String, Locale>, Locale>()

private val icuLocales: Set<String> by lazy { ULocale.getAvailableLocales().map { it.toLanguageTag() }.toSet() }
