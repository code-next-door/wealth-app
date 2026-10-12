package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
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
 * A statement over several months also gives estimated month-ends (for building
 * history): each fund's units that day (exact, from its transactions) × the NAV
 * of its latest transaction on or before it (before its first one, the earliest
 * NAV the statement gives it). Only when every fund's units add up.
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
        val period = PERIOD.find(text)?.groupValues
        val from = period?.get(1)?.let(::date)
        val to = period?.get(2)?.let(::date)
        val funds = mutableListOf<Fund>()
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
            funds += Fund(OPENING.find(block)?.groupValues?.get(1)?.let(::number), TRADE.findAll(block).mapNotNull(::trade).toList(), units, nav)
        }
        val unitsAddUp = funds.all { it.unitsAddUp() }
        if (!unitsAddUp) needsCheck = true
        val estimates = if (from == null || to == null || !unitsAddUp) emptyList() else {
            generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .map { it.atEndOfMonth() }
                .takeWhile { it.isBefore(to) }
                .map { day -> day to funds.fold(BigDecimal.ZERO) { sum, fund -> sum + fund.valueOn(day) } }
                .toList()
        }
        return ParsedStatement(
            format = "Mutual fund statement (CAS)",
            currency = "INR",
            transactions = emptyList(),
            closingBalance = total,
            closingDate = to,
            valueNeedsCheck = needsCheck,
            accountTypeKey = "in_mutual_funds",
            balances = estimates,
            balancesEstimated = estimates.isNotEmpty(),
        )
    }

    /** A purchase or redemption: [units] signed (redemptions negative), the NAV [price], and the [balance] after it. */
    private data class Trade(val date: LocalDate, val units: BigDecimal, val price: BigDecimal, val balance: BigDecimal)

    private data class Fund(val opening: BigDecimal?, val trades: List<Trade>, val closingUnits: BigDecimal, val closingNav: BigDecimal) {
        /**
         * Opening units + each trade = each printed balance, and the last one = the closing units.
         * A row that leaves the balance unchanged (a rejected or reversed instalment) is fine too.
         */
        fun unitsAddUp(): Boolean {
            var held = opening ?: return false
            trades.forEach {
                val added = (held + it.units - it.balance).abs() <= UNIT_TOLERANCE
                val unchanged = (held - it.balance).abs() <= UNIT_TOLERANCE
                if (!added && !unchanged) return false
                held = it.balance
            }
            return (held - closingUnits).abs() <= UNIT_TOLERANCE
        }

        fun valueOn(day: LocalDate): BigDecimal {
            val last = trades.lastOrNull { !it.date.isAfter(day) }
            val units = last?.balance ?: opening ?: BigDecimal.ZERO
            val nav = last?.price ?: trades.firstOrNull()?.price ?: closingNav
            return (units * nav).setScale(2, RoundingMode.HALF_EVEN)
        }
    }

    private fun trade(match: MatchResult): Trade? {
        val (day, units, price, balance) = match.destructured
        val signed = number(units.trim('(', ')')).let { if (units.startsWith("(") || units.startsWith("-")) it.abs().negate() else it }
        return date(day)?.let { Trade(it, signed, number(price), number(balance)) }
    }

    private fun number(text: String) = BigDecimal(text.replace(",", "").removePrefix("-"))

    private fun date(text: String) = runCatching { LocalDate.parse(text, DATE) }.getOrNull()

    private companion object {
        const val CLOSING_LABEL = "Closing Unit Balance:"
        /** Units are printed with 3 decimals. */
        val UNIT_TOLERANCE = BigDecimal("0.001")
        val OPENING = Regex("""Opening Unit Balance:\s*([\d,]+(?:\.\d+)?)""")

        // "05-Jul-2026 Purchase - SIP 5,000.00 110.444 45.2718 1,234.567": date, text, amount, units, NAV, units after.
        // Redemptions show amount and units in brackets; rows without units (stamp duty) don't match.
        val TRADE = Regex("""(?m)^\s*(\d{2}-[A-Za-z]{3}-\d{4})\s.*?\s\(?-?[\d,]+\.\d+\)?\s+(\(?-?[\d,]+\.\d+\)?)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s*$""")
        val PERIOD = Regex("""From Date\s*:\s*(\d{2}-[A-Za-z]{3}-\d{4})\s*To Date\s*:\s*(\d{2}-[A-Za-z]{3}-\d{4})""")

        // "Closing Unit Balance: 1,234.567 Nav as on 31-JUL-2026: INR 45.6789 Valuation on 31-Jul-2026 : INR 56,393.66"
        // (\s also matches a line break, in case the line wraps).
        val CLOSING = Regex(
            """Closing Unit Balance:\s*([\d,]+(?:\.\d+)?)\s+Nav as on\s+[^:]+:\s*INR\s*([\d,]+(?:\.\d+)?)\s+Valuation on\s+[^:]+:\s*INR\s*([\d,]+(?:\.\d+)?)""",
        )
        val DATE: DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH)
    }
}
