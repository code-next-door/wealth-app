package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HDFC Bank account statement, net-banking layout (the PDFs downloaded for
 * 2021–2024), as Android extracts the text. The other HDFC layout is
 * [HdfcStatementParser].
 *
 * A row is "DD/MM/YYYY <narration> <ref> DD/MM/YYYY <withdrawal> <deposit>
 * <closing balance>", one of the two amounts being 0.00. Long narrations wrap
 * mid-word onto more lines (the first may hold only the date); the amounts
 * end the row. Each page repeats a header block, up to "Expected AMB", and
 * ends with a footer ("Generation Date…"); rows are only read in between.
 *
 * The totals at the end (opening and closing balance, debits, credits) must
 * agree with the rows and their running balance, or every row is flagged.
 */
class HdfcNetBankingParser : StatementParser {

    override fun canParse(text: String): Boolean = text.contains("HDFC") && text.contains(HEADER)

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<Row>()
        val totals = HashMap<String, BigDecimal>()

        var inRows = false
        // The row being read: its date and narration so far.
        var date: LocalDate? = null
        val narration = StringBuilder()
        var pendingLabel: String? = null

        fun complete(prefix: String, match: MatchResult) {
            // The text before the amounts: the narration's last part, then the reference (if
            // the row has one), which isn't part of the description.
            val tail = prefix.trim().let { text -> REFERENCE.matchEntire(text)?.let { it.groups[1]?.value.orEmpty() } ?: text }
            narration.append(tail)
            val description = narration.toString().trim()
            val (withdrawal, deposit, balance) = match.destructured.toList().takeLast(3).map(::amount)
            rows += Row(date!!, description, withdrawal, deposit, balance)
            date = null
            narration.clear()
        }

        for (line in lines) {
            when {
                line.startsWith(HEADER_START) || line.startsWith("Expected AMB") -> inRows = true
                line.startsWith("Generation Date") || line.startsWith("**END OF STATEMENT") -> inRows = false
                line in TOTALS -> {
                    inRows = false
                    pendingLabel = line
                }
                pendingLabel != null -> {
                    AMOUNT_ONLY.matchEntire(line)?.let { totals[pendingLabel] = amount(it.groupValues[1]) }
                    pendingLabel = null
                }
                !inRows -> Unit
                ROW_START.find(line) != null -> {
                    // A new row; a full one when its amounts are on the same line.
                    date = LocalDate.parse(ROW_START.find(line)!!.groupValues[1], LONG_DATE)
                    narration.clear()
                    val rest = line.substringAfter(" ", "")
                    val full = AMOUNTS.matchEntire(rest)
                    if (full != null) complete(full.groupValues[1], full) else narration.append(rest.trim())
                }
                date != null -> {
                    val end = AMOUNTS.matchEntire(line)
                    if (end != null) complete(end.groupValues[1], end) else narration.append(line)
                }
            }
        }

        val period = lines.firstNotNullOfOrNull { PERIOD.find(it) }
        val start = period?.let { LocalDate.parse(it.groupValues[1], SHORT_DATE) }
        val end = period?.let { LocalDate.parse(it.groupValues[2], SHORT_DATE) }
        val opening = totals["Opening Balance"]
        val closing = totals["Closing Balance"]

        // Everything must agree: each row with the running balance, and the rows with the totals.
        var running = opening
        val rowsAgree = rows.all { row ->
            val change = row.deposit - row.withdrawal
            // One side only, or neither (a failed payment: no money moves).
            val oneSided = row.withdrawal.signum() == 0 || row.deposit.signum() == 0
            val agrees = oneSided && running?.let { (it + change).compareTo(row.balance) == 0 } == true
            running = row.balance
            agrees
        }
        val debits = rows.fold(BigDecimal.ZERO) { s, r -> s + r.withdrawal }
        val credits = rows.fold(BigDecimal.ZERO) { s, r -> s + r.deposit }
        val totalsAgree = opening != null && closing != null &&
            totals["Debits"]?.compareTo(debits) == 0 && totals["Credits"]?.compareTo(credits) == 0 &&
            (opening - debits + credits).compareTo(closing) == 0
        val needsCheck = !(rowsAgree && totalsAgree)

        return ParsedStatement(
            format = "HDFC account statement",
            currency = if (lines.any { it.endsWith(": INR") }) "INR" else null,
            // A row with 0.00 both ways moved no money: not a transaction (its balance still counts).
            transactions = rows.filter { (it.deposit - it.withdrawal).signum() != 0 }
                .map { StatementTransaction(it.date, it.deposit - it.withdrawal, it.description, needsCheck) },
            closingBalance = closing ?: rows.lastOrNull()?.balance,
            closingDate = end ?: rows.lastOrNull()?.date,
            balances = rows.map { it.date to it.balance },
            openingBalance = opening,
            openingDate = start?.minusDays(1),
        )
    }

    private data class Row(val date: LocalDate, val description: String, val withdrawal: BigDecimal, val deposit: BigDecimal, val balance: BigDecimal)

    /** "1,23,456.78" (Indian grouping) or "123,456.78". */
    private fun amount(text: String): BigDecimal = BigDecimal(text.replace(",", ""))

    private companion object {
        const val HEADER_START = "Date Narration"
        const val HEADER = "Withdrawal Amount Deposit Amount Closing Balance"
        val TOTALS = setOf("Opening Balance", "Closing Balance", "Debits", "Credits")
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val AMOUNT = """([\d,]+\.\d{2})"""

        /** A row's first line starts with its date (alone, or followed by narration). */
        val ROW_START = Regex("""^(\d{2}/\d{2}/\d{4})(?:\s|$)""")

        /** The end of a row: text, the value date, withdrawal, deposit and the balance (which can be below zero). */
        val AMOUNTS = Regex("""^(.*?)\s*\d{2}/\d{2}/\d{4} $AMOUNT $AMOUNT (-?[\d,]+\.\d{2})$""")
        val AMOUNT_ONLY = Regex("""^$AMOUNT$""")

        /** Text ending with a reference (a long run of digits, maybe with capitals, e.g. "000012345678"): group 1 is the text before it. */
        val REFERENCE = Regex("""^(?:(.*?)\s+)?[0-9A-Z]*\d{6,}[0-9A-Z]*$""")
        val PERIOD = Regex("""Statement From : (\d{2}/\d{2}/\d{2}) TO : (\d{2}/\d{2}/\d{2})""")
    }
}
