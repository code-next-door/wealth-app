package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * VIAC (Swiss pillar 3a) "Reporting" PDF, one per month. Like the mutual fund
 * statement, only the total matters: one CHF balance on the "Reporting as of"
 * day, from the "Total" row of the portfolio table on page 1. Deposits and
 * trades inside the 3a aren't spending, so transactions aren't imported.
 *
 * The portfolio rows above it must add up to the total, or the value is
 * flagged for review (as it is without a total row or a readable date).
 */
class ViacStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("www.viac.ch") && text.contains("Reporting as of") && text.contains(TABLE_HEADER)

    override fun parse(text: String): ParsedStatement {
        val date = AS_OF.find(text)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() }
        // The first table only: the same portfolio rows come again on later pages.
        val table = text.lines().map(String::trim).dropWhile { !it.startsWith(TABLE_HEADER) }.drop(1)
            .takeWhile { !it.startsWith("Contract") }
        val portfolios = table.mapNotNull { PORTFOLIO.matchEntire(it)?.groupValues?.last()?.let(::amount) }
        val total = table.firstNotNullOfOrNull { TOTAL.matchEntire(it)?.groupValues?.last()?.let(::amount) }
        val sum = portfolios.fold(BigDecimal.ZERO, BigDecimal::add)
        return ParsedStatement(
            format = "VIAC pillar 3a report",
            currency = "CHF",
            transactions = emptyList(),
            closingBalance = total ?: sum.takeIf { portfolios.isNotEmpty() },
            closingDate = date,
            valueNeedsCheck = date == null || total == null || total.compareTo(sum) != 0,
            issuer = "VIAC",
            accountTypeKey = if (text.contains("Pillar 3a")) "ch_pillar3a" else null,
        )
    }

    /** "12'345.67" (straight or typographic apostrophe), maybe negative. */
    private fun amount(text: String) = BigDecimal(text.replace("'", "").replace("’", ""))

    private companion object {
        const val TABLE_HEADER = "Number Name Strategy Deposits"
        const val NUMBER = """-?[\d'’]+\.\d{2}"""
        const val RETURNS = """(-?[\d.]+)% ($NUMBER) ($NUMBER)"""
        val AS_OF = Regex("""Reporting as of (\d{2}\.\d{2}\.\d{4})""")

        // "1.234.567.890.01 Portfolio 1 Global 80 with bonds 7'056.00 8.12% 573.10 7'629.10": deposits, return %, return, balance.
        val PORTFOLIO = Regex("""\d+(?:\.\d+)+ .+ $NUMBER $RETURNS""")
        val TOTAL = Regex("""Total $NUMBER $RETURNS""")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
