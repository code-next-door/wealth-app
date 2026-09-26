package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

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
 * Formats an amount with its currency using the device's regional style,
 * e.g. "CHF 1,234.50" (en-CH shows "CHF 1’234.50"; en-IN shows "₹1,23,456.00").
 * Rounds half-even to the currency's decimals.
 */
fun formatMoney(
    amount: BigDecimal,
    currencyCode: String,
    decimals: Int,
    locale: Locale = Locale.getDefault(),
): String {
    val format = NumberFormat.getCurrencyInstance(locale).apply {
        runCatching { currency = java.util.Currency.getInstance(currencyCode) }
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_EVEN
    }
    return format.format(amount.setScale(decimals, RoundingMode.HALF_EVEN))
}
