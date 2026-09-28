package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Indian mutual funds: the Consolidated Account Statement (CAS) from CAMS and
 * KFintech (e.g. MFCentral's detailed PDF). Like Zerodha's export, only the
 * total matters (author's choice): one balance, in INR, on the statement's
 * "To Date" = the sum of every fund's "Valuation" on its closing line. Buying
 * or redeeming funds isn't spending, so transactions aren't imported.
 *
 * Checks, any of which flags the value for review:
 * - each fund's value against its units × NAV;
 * - every closing line must be readable;
 * - funds without a folio number are left out: those are demat holdings,
 *   already counted in a broker's export (Zerodha), so they'd count twice.
 */
class MutualFundCasParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("Consolidated Account Statement") && text.contains(CLOSING_LABEL)

    override fun parse(text: String): ParsedStatement {
        val to = PERIOD.find(text)?.groupValues?.get(2)?.let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() }
        var total = BigDecimal.ZERO
        var needsCheck = to == null
        var blockStart = 0
        val starts = Regex(Regex.escape(CLOSING_LABEL)).findAll(text).map { it.range.first }.toList()
        starts.forEach { start ->
            val block = text.substring(blockStart, start)
            val line = CLOSING.find(text, start)?.takeIf { it.range.first == start }
            blockStart = line?.range?.last?.plus(1) ?: (start + CLOSING_LABEL.length)
            if (line == null) {
                needsCheck = true
                return@forEach
            }
            val (units, nav, value) = line.destructured.toList().map { BigDecimal(it.replace(",", "")) }
            if (!block.contains("FOLIO NO")) {
                needsCheck = true
                return@forEach
            }
            // Units have 3 decimals and NAVs 4; more than a rupee apart is a real difference.
            if ((units * nav - value).abs() > BigDecimal.ONE) needsCheck = true
            total += value
        }
        return ParsedStatement(
            format = "Mutual fund statement (CAS)",
            currency = "INR",
            transactions = emptyList(),
            closingBalance = total,
            closingDate = to,
            valueNeedsCheck = needsCheck,
            accountTypeKey = "in_mutual_funds",
        )
    }

    private companion object {
        const val CLOSING_LABEL = "Closing Unit Balance:"
        val PERIOD = Regex("""From Date\s*:\s*(\d{2}-[A-Za-z]{3}-\d{4})\s*To Date\s*:\s*(\d{2}-[A-Za-z]{3}-\d{4})""")

        // "Closing Unit Balance: 1,234.567 Nav as on 31-JUL-2026: INR 45.6789 Valuation on 31-Jul-2026 : INR 56,393.66"
        // (\s also matches a line break, in case the line wraps).
        val CLOSING = Regex(
            """Closing Unit Balance:\s*([\d,]+(?:\.\d+)?)\s+Nav as on\s+[^:]+:\s*INR\s*([\d,]+(?:\.\d+)?)\s+Valuation on\s+[^:]+:\s*INR\s*([\d,]+(?:\.\d+)?)""",
        )
        val DATE: DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH)
    }
}
