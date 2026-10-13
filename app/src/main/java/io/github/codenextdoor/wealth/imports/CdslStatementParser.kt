package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.domain.DematHoldings
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * CDSL demat "Transaction Statement" (PDF, as Android extracts it). Its real figure
 * is the "Portfolio Valuation" on the holdings day. Per share (ISIN) it also lists the
 * quantity after every change, which with downloaded prices gives month-ends (see
 * [DematHoldings]); only when every share's changes reach its closing balance.
 * Buying shares isn't spending, so no transactions are imported.
 *
 * Checks, any of which flags the value for review: quantities that don't reach the
 * closing balance, holdings that don't add up to the valuation.
 */
class CdslStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("TRANSACTION STATEMENT") && text.contains("DP ID") &&
            (text.contains("STATEMENT OF ACCOUNT FOR THE PERIOD") || text.contains("No Holding"))

    override fun parse(text: String): ParsedStatement {
        val period = PERIOD.find(text)?.groupValues
        val from = period?.get(1)?.let(::date)
        val asOn = AS_ON.find(text)?.groupValues?.get(1)?.let(::date) ?: period?.get(2)?.let(::date)
        val valuation = VALUATION.find(text)?.groupValues?.get(1)?.let(::number)
        // The holdings at the end repeat ISINs and have number rows of their own.
        val cut = text.indexOf("STATEMENT OF HOLDINGS").takeIf { it >= 0 } ?: text.length
        val (accounts, holdings) = text.substring(0, cut) to text.substring(cut)

        val quantities = linkedMapOf<String, List<Pair<LocalDate, BigDecimal>>>()
        var unitsAddUp = true
        val headers = ISIN_HEADER.findAll(accounts).toList()
        headers.forEachIndexed { i, header ->
            val block = accounts.substring(header.range.last + 1, headers.getOrNull(i + 1)?.range?.first ?: accounts.length)
            val changes = mutableListOf<Pair<LocalDate, BigDecimal>>()
            var pending: LocalDate? = null
            var closing: BigDecimal? = null
            block.lines().map { it.trim() }.forEach { line ->
                val opening = OPENING.matchEntire(line)
                val close = CLOSING.matchEntire(line)
                val dated = DATED.matchEntire(line)
                val units = UNITS.matchEntire(line)
                when {
                    opening != null -> date(opening.groupValues[1])?.let { changes += it to number(opening.groupValues[2]) }
                    close != null -> closing = number(close.groupValues[2])
                    dated != null -> pending = date(dated.groupValues[1])
                    // "quantity balance-after": the balance is what's held from that day.
                    units != null -> pending?.let { changes += it to number(units.groupValues[2]); pending = null }
                }
            }
            val last = changes.lastOrNull()?.second
            if (last == null || closing == null || (last - closing!!).abs() > UNIT_TOLERANCE) unitsAddUp = false
            quantities[header.groupValues[1]] = changes
        }

        val prices = mutableMapOf<String, BigDecimal>()
        var holdingsTotal = BigDecimal.ZERO
        val isins = ISIN.findAll(holdings).toList()
        isins.forEachIndexed { i, isin ->
            val end = isins.getOrNull(i + 1)?.range?.first ?: holdings.length
            val row = HOLDING_ROW.find(holdings.substring(isin.range.last + 1, end)) ?: return@forEachIndexed
            val units = number(row.groupValues[1])
            val value = number(row.groupValues[2])
            holdingsTotal += value
            if (units.signum() > 0) prices[isin.value] = value.divide(units, 4, RoundingMode.HALF_EVEN)
            // Only shares with transactions get a block above: one without was held unchanged all period.
            if (from != null && isin.value !in quantities) quantities[isin.value] = listOf(from to units)
        }

        val needsCheck = !unitsAddUp || (valuation != null && (holdingsTotal - valuation).abs() > BigDecimal.ONE)
        val demat = if (from != null && asOn != null && unitsAddUp && quantities.isNotEmpty()) DematHoldings(from, asOn, quantities, prices) else null
        return ParsedStatement(
            format = "CDSL demat statement",
            currency = "INR",
            transactions = emptyList(),
            closingBalance = valuation,
            closingDate = asOn.takeIf { valuation != null },
            valueNeedsCheck = needsCheck,
            accountTypeKey = "in_stocks",
            demat = demat,
        )
    }

    private fun number(text: String) = BigDecimal(text.replace(",", ""))

    private fun date(text: String) = runCatching { LocalDate.parse(text, DATE) }.getOrNull()

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        /** Quantities are printed with 3 decimals. */
        val UNIT_TOLERANCE = BigDecimal("0.001")
        val PERIOD = Regex("""STATEMENT OF ACCOUNT FOR THE PERIOD FROM:\s*(\d{2}-\d{2}-\d{4})\s*TO:\s*(\d{2}-\d{2}-\d{4})""")
        val AS_ON = Regex("""STATEMENT OF HOLDINGS AS ON:\s*(\d{2}-\d{2}-\d{4})""")
        // "Portfolio Valuation : ₹ 3,300.00" (the rupee sign may extract as anything).
        val VALUATION = Regex("""Portfolio Valuation\s*:\s*\D*?([\d,]+\.\d{2})""")
        val ISIN_HEADER = Regex("""ISIN\s*:\s*(IN[A-Z0-9]{9}\d)""")
        /** An Indian ISIN, possibly run into the share's name in the holdings. */
        val ISIN = Regex("""IN[A-Z0-9]{9}\d""")
        val OPENING = Regex("""(\d{2}-\d{2}-\d{4})\s+Opening Balance\s+([\d,]+\.\d+)""")
        val CLOSING = Regex("""(\d{2}-\d{2}-\d{4})\s+Closing Balance\s+([\d,]+\.\d+)""")
        val DATED = Regex("""(\d{2}-\d{2}-\d{4})\s+.*""")
        val UNITS = Regex("""([\d,]+\.\d{3})\s+([\d,]+\.\d{3})""")
        // Holdings: "current free lock-in value", e.g. "15.000 15.000 0.000 1,500.00", on a line
        // of its own or (at a page break) at the end of the ISIN and name's line.
        val HOLDING_ROW = Regex("""(?m)(?:^|\s)([\d,]+\.\d{3})\s+[\d,]+\.\d{3}\s+[\d,]+\.\d{3}\s+([\d,]+\.\d{2})\s*$""")
    }
}
