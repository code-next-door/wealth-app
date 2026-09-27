package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interactive Brokers activity statement (PDF, English), as Android extracts
 * the text. Only the account's total value (Net Asset Value: stocks and cash,
 * in the base currency) matters here: "Starting Value" on the day before the
 * period and "Ending Value" at its end. Trades, deposits and dividends aren't
 * spending, so no rows are imported.
 */
class IbkrActivityStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("Interactive Brokers") && text.contains("Activity Statement") && text.contains("Net Asset Value")

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val period = lines.firstNotNullOfOrNull { PERIOD.matchEntire(it) }
        fun value(label: String) = lines.firstNotNullOfOrNull { Regex("^$label $AMOUNT$").matchEntire(it) }
            ?.groupValues?.get(1)?.let { BigDecimal(it.replace(",", "")) }
        return ParsedStatement(
            format = "Interactive Brokers activity statement",
            currency = lines.firstNotNullOfOrNull { CURRENCY.matchEntire(it)?.groupValues?.get(1) },
            transactions = emptyList(),
            closingBalance = value("Ending Value"),
            closingDate = period?.let { LocalDate.parse(it.groupValues[2], DATE_FORMAT) },
            openingBalance = value("Starting Value"),
            // The starting value is the previous day's close.
            openingDate = period?.let { LocalDate.parse(it.groupValues[1], DATE_FORMAT).minusDays(1) },
        )
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        const val DAY = """([A-Z][a-z]+ \d{1,2}, \d{4})"""
        const val AMOUNT = """(-?[\d,]+\.\d{2})"""
        val PERIOD = Regex("^$DAY - $DAY$")
        val CURRENCY = Regex("""^Base Currency ([A-Z]{3})$""")
    }
}
