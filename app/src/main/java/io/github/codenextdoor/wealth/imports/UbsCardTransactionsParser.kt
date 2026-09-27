package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * UBS e-banking credit card transactions report (PDF, English), as Android
 * extracts the text. Each booking is a block: "DD.MM.YYYY <text> [country]",
 * the merchant's category, sometimes exchange-rate lines, the card, and
 * "CCY amount [CHF amount] <booked date>".
 *
 * Debits and credits only differ by column, and there's no running balance.
 * Reading the PDF marks amounts in the Credit column (see [CreditColumn]); card
 * bill payments ("DIRECT DEBIT") also count as credits where positions aren't
 * available. The report's debit and credit totals must then match, or every
 * row is flagged.
 */
class UbsCardTransactionsParser : StatementParser {

    override val creditColumn = CreditColumn(
        header = "Purchase Booking text Amount Debit Credit Booked",
        // Look for the francs: the last "CCY amount" before the booking date.
        amountLine = Regex("""^(?:[A-Z]{3} [\d'’.]+ )?([A-Z]{3} [\d'’]+\.\d{2}) \d{2}\.\d{2}\.\d{4}$"""),
    )

    override fun canParse(text: String): Boolean = text.contains(REPORT_MARK) && text.contains(HEADER_MARK)

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<StatementTransaction>()
        var debits = BigDecimal.ZERO
        var credits = BigDecimal.ZERO
        var totals: Pair<BigDecimal, BigDecimal>? = null
        var currency: String? = null

        var date: LocalDate? = null
        var bookingText = ""
        var category: String? = null
        var afterStart = false

        for (line in lines) {
            TOTALS.matchEntire(line)?.let { m ->
                currency = m.groupValues[1]
                totals = amount(m.groupValues[2]) to amount(m.groupValues[3])
                date = null
                return@let
            } ?: AMOUNT_LINE.matchEntire(line)?.let { m ->
                val d = date ?: return@let
                // The francs when a foreign amount comes first.
                val value = amount(if (m.groupValues[4].isNotEmpty()) m.groupValues[4] else m.groupValues[2])
                val credit = m.groupValues[5].isNotEmpty() || CREDITS.any { bookingText.startsWith(it) }
                if (credit) credits += value else debits += value
                rows += StatementTransaction(d, if (credit) value else value.negate(), listOfNotNull(bookingText, category).joinToString(" · "))
                date = null
            } ?: START.matchEntire(line)?.let { m ->
                date = LocalDate.parse(m.groupValues[1], DATE_FORMAT)
                bookingText = m.groupValues[2].trim().replace(COUNTRY, "")
                category = null
                afterStart = true
            } ?: run {
                // The line right after the booking text is its category (bill payments have none).
                if (date != null && afterStart && !CARD.matches(line) && !line.startsWith("Exchange rate") && !line.startsWith("Processing fee")) {
                    category = line
                }
                afterStart = false
            }
        }

        val matches = totals?.let { (debit, credit) -> debit.compareTo(debits) == 0 && credit.compareTo(credits) == 0 } == true
        return ParsedStatement(
            format = "UBS credit card transactions",
            currency = currency ?: "CHF",
            transactions = CardStatements.flagUnlessMatching(rows, matches),
            fromCard = true,
        )
    }

    private fun amount(text: String): BigDecimal = BigDecimal(text.replace("'", "").replace("’", ""))

    private companion object {
        const val REPORT_MARK = "UBS e-banking / Credit cards"
        const val HEADER_MARK = "Purchase Booking text"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        const val AMOUNT = """(-?[\d'’]+\.\d{2})"""

        val START = Regex("""^(\d{2}\.\d{2}\.\d{4}) (.+)$""")
        /** "CHF 42.50 06.01.2026", or "USD 20.00 CHF 18.11 12.01.2026" for a foreign purchase. */
        val AMOUNT_LINE = Regex("""^([A-Z]{3}) $AMOUNT(?: ([A-Z]{3}) $AMOUNT)? \d{2}\.\d{2}\.\d{4}(${CreditColumn.MARK})?$""")
        val TOTALS = Regex("""^Total card bookings ([A-Z]{3}) $AMOUNT $AMOUNT $AMOUNT$""")
        val CARD = Regex("""^\d{4} [\dX]{4} [\dX]{4}(?: [\dX]{4})?, .*""")
        val COUNTRY = Regex(""" [A-Z]{3}$""")

        /** Booking texts that are money onto the card. */
        val CREDITS = listOf("DIRECT DEBIT")
    }
}
