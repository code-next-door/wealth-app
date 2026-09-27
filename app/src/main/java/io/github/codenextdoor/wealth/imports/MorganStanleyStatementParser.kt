package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Morgan Stanley StockPlan Connect quarterly statement (PDF, English), as
 * Android extracts the text. Its summary lists opening and closing values on
 * one line each ("Number of Shares 100.000 112.500"); the closing ones are
 * what's held at the end of the period. The activity (vests, dividends, tax)
 * isn't spending, so no rows are imported.
 */
class MorganStanleyStatementParser : StatementParser {

    override fun canParse(text: String): Boolean = text.contains("Morgan Stanley") && text.contains(SUMMARY)

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun closing(label: String): BigDecimal? = lines.firstNotNullOfOrNull { line ->
            Regex("^$label $VALUE $VALUE$").matchEntire(line)?.groupValues?.get(2)?.let(::amount)
        }
        val units = closing("Number of Shares")
        val price = closing("Share Price")
        val cash = (closing("Cash Value") ?: BigDecimal.ZERO) + (closing("Net Unsettled Cash") ?: BigDecimal.ZERO)
        val total = closing("Total Account Value")
        // "(as of 3/31/26)" after "Closing Value".
        val closingDate = lines.dropWhile { it != "Closing Value" }.firstNotNullOfOrNull { AS_OF.matchEntire(it)?.groupValues?.get(1) }
            ?.let { LocalDate.parse(it, DATE_FORMAT) }
        val holdings = if (units != null && price != null) {
            val value = units * price + cash
            Holdings(units, price, cash, addsUp = total != null && (value - total).abs() <= BigDecimal("0.01"))
        } else {
            null
        }
        return ParsedStatement(
            format = "Morgan Stanley stock plan statement",
            currency = "USD",
            transactions = emptyList(),
            closingBalance = total,
            closingDate = closingDate,
            holdings = holdings,
        )
    }

    /** "$1,234.5600", "1,234.567" or "(1.50)" for a negative amount. */
    private fun amount(text: String): BigDecimal {
        val negative = text.startsWith("(")
        val value = BigDecimal(text.trim('(', ')').removePrefix("$").replace(",", ""))
        return if (negative) value.negate() else value
    }

    private companion object {
        const val SUMMARY = "Share Purchase and Holdings Summary"
        const val VALUE = """(\(?\$?[\d,]+(?:\.\d+)?\)?)"""
        val AS_OF = Regex("""^\(as of (\d{1,2}/\d{1,2}/\d{2})\)$""")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy")
    }
}
