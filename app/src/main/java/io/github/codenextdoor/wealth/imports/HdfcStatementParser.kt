package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HDFC Bank account statement (PDF), as Android extracts the text.
 *
 * A row is "DD/MM/YY <narration> <ref> DD/MM/YY <amount> <balance>". Long
 * narrations wrap: the first line has the date, the amounts sit on a line
 * starting with the reference, and more of the narration can follow, even
 * after a page's header block (which Android puts between the rows).
 *
 * Withdrawals and deposits land in the same place, so the direction comes
 * from the running balance. There's no opening balance, so the first row is
 * taken as money out and flagged for checking.
 */
class HdfcStatementParser : StatementParser {

    override fun canParse(text: String): Boolean = text.contains("HDFC") && text.contains(HEADER_MARK)

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val transactions = mutableListOf<StatementTransaction>()
        val balances = mutableListOf<Pair<LocalDate, BigDecimal>>()

        var previousBalance: BigDecimal? = null
        // The row being read.
        var date: LocalDate? = null
        val narration = mutableListOf<String>()
        var amount: BigDecimal? = null
        var needsCheck = false
        var inPageBlock = false

        fun finish() {
            val d = date ?: return
            val a = amount ?: return
            transactions += StatementTransaction(d, a, narration.joinToString(" "), needsCheck)
            date = null
            amount = null
            narration.clear()
        }

        fun amounts(printed: BigDecimal, balance: BigDecimal) {
            val change = previousBalance?.let { balance - it }
            amount = if (change != null && change.signum() > 0) printed else printed.negate()
            needsCheck = change == null || change.abs().compareTo(printed) != 0
            previousBalance = balance
            balances += date!! to balance
        }

        for (line in lines) {
            val full = FULL_ROW.matchEntire(line)
            val start = if (full == null) ROW_START.matchEntire(line) else null
            when {
                full != null -> {
                    finish()
                    inPageBlock = false
                    date = parseDate(full.groupValues[1])
                    narration += full.groupValues[2].trim()
                    amounts(amount(full.groupValues[5]), amount(full.groupValues[6]))
                }
                start != null -> {
                    finish()
                    inPageBlock = false
                    date = parseDate(start.groupValues[1])
                    narration += start.groupValues[2].trim()
                }
                line.startsWith("Page No .") -> inPageBlock = true
                inPageBlock -> if (line.startsWith("Registered Office Address")) inPageBlock = false
                NOISE.any { line.startsWith(it) } -> Unit
                date != null && amount == null -> AMOUNTS.matchEntire(line)?.let { m ->
                    amounts(amount(m.groupValues[3]), amount(m.groupValues[4]))
                } ?: run { narration += line }
                date != null -> narration += line
            }
        }
        finish()

        val period = lines.firstNotNullOfOrNull { PERIOD.find(it) }
        return ParsedStatement(
            format = "HDFC account statement",
            currency = lines.firstNotNullOfOrNull { CURRENCY.find(it)?.groupValues?.get(1) },
            transactions = transactions,
            closingBalance = balances.lastOrNull()?.second,
            closingDate = period?.let { LocalDate.parse(it.groupValues[1], LONG_DATE) } ?: balances.lastOrNull()?.first,
            balances = balances,
        )
    }

    private fun parseDate(text: String): LocalDate = LocalDate.parse(text, SHORT_DATE)

    /** "1,23,456.78" (Indian grouping) or "-50.00". */
    private fun amount(text: String): BigDecimal = BigDecimal(text.replace(",", ""))

    private companion object {
        const val HEADER_MARK = "Chq./Ref.No."
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val DATE = """(\d{2}/\d{2}/\d{2})"""
        const val AMOUNT = """(-?[\d,]+\.\d{2})"""

        val FULL_ROW = Regex("^$DATE (.+) (\\S+) $DATE $AMOUNT $AMOUNT$")
        val ROW_START = Regex("^$DATE (.+)$")
        val AMOUNTS = Regex("^(\\S+) $DATE $AMOUNT $AMOUNT$")
        val PERIOD = Regex("""Statement From : \d{2}/\d{2}/\d{4} To : (\d{2}/\d{2}/\d{4})""")
        val CURRENCY = Regex("""Currency : ([A-Z]{3})""")

        /** Lines that are neither rows nor narration. */
        val NOISE = listOf("Date Narration", "This is a computer generated statement", "not require signature", "Generated On:")
    }
}
