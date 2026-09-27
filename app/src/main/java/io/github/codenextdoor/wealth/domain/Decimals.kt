package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.MathContext

/**
 * Turns user-typed numbers into a plain "1234.5" form. Handles:
 * - "," or "." as the decimal separator ("105,26", "105.26")
 * - thousands separators: "1,234.50", "1.234,50", "1'234.50", "1 234,50",
 *   and Indian grouping "1,23,456.78"
 * When both "," and "." appear, whichever comes last is the decimal
 * separator. A lone "," is a decimal separator only when followed by 1–2
 * digits ("12,5"), otherwise a thousands separator ("1,234").
 */
internal fun normalizeNumberInput(input: String): String {
    var s = input.trim().replace("'", "").replace(" ", "").replace(" ", "")
    val lastComma = s.lastIndexOf(',')
    val lastDot = s.lastIndexOf('.')
    s = when {
        lastComma >= 0 && lastDot >= 0 ->
            if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        lastComma >= 0 && s.count { it == ',' } == 1 && s.length - lastComma - 1 in 1..2 ->
            s.replace(',', '.')
        else -> s.replace(",", "")
    }
    return s
}

/** Parses a number > 0 typed by the user (see [normalizeNumberInput]), or null. */
fun parsePositiveDecimal(input: String): BigDecimal? =
    normalizeNumberInput(input).toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

/** Parses a number >= 0 typed by the user (e.g. a number of shares), or null. */
fun parseNonNegativeDecimal(input: String): BigDecimal? =
    normalizeNumberInput(input).toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

/** Shows a rate with up to 10 significant digits and no trailing zeros. */
fun formatRate(rate: BigDecimal): String =
    rate.round(MathContext(10)).stripTrailingZeros().toPlainString()
