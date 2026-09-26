package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.MathContext

/**
 * Parses a positive number typed by the user. Accepts "," or "." as the
 * decimal separator and ignores spaces and Swiss-style thousands
 * separators ("1'000.50"). Returns null if the input isn't a number > 0.
 */
fun parsePositiveDecimal(input: String): BigDecimal? {
    val cleaned = input.trim()
        .replace("'", "")
        .replace(" ", "")
        .replace(",", ".")
    val value = cleaned.toBigDecimalOrNull() ?: return null
    return value.takeIf { it.signum() > 0 }
}

/** Shows a rate with up to 10 significant digits and no trailing zeros. */
fun formatRate(rate: BigDecimal): String =
    rate.round(MathContext(10)).stripTrailingZeros().toPlainString()
