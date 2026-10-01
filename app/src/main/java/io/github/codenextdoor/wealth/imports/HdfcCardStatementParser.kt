package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * HDFC Bank credit card statement (PDF), as Android extracts the text. Two layouts:
 *
 * - 2021–2024: "Statement Date:dd/MM/yyyy"; rows "dd/MM/yyyy [HH:mm:ss] text [points]
 *   amount [Cr]"; the account summary's values (opening, payments/credits,
 *   purchases/debits, finance charges, total dues) on the line after "Charges Total Dues".
 * - 2026: the rupee sign comes out as "C"; rows "dd/MM/yyyy| HH:mm text [+ points]
 *   [+] C amount l" (a "+" right before the amount marks a credit), sometimes wrapped
 *   onto the next line; "TOTAL AMOUNT DUE" above its value; the summary values
 *   (previous dues, payments/credits, purchases/debit, finance charges) on one line;
 *   the statement date ends the "Billing Period".
 *
 * Rows are only read inside the transaction tables (domestic and international). They
 * must add up to the summary's debits and credits, and the summary to the total due
 * (which is rounded to the rupee), or every row is flagged.
 */
class HdfcCardStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("HDFC Bank Credit Cards") && text.contains("Credit Card Statement")

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<StatementTransaction>()

        var inRows = false
        // A row whose amount is on a later line: its date and text so far.
        var pendingDate: LocalDate? = null
        var pendingNew = false
        val pendingText = StringBuilder()

        fun add(date: LocalDate, description: String, amount: String, credit: Boolean) {
            val value = amount(amount)
            rows += StatementTransaction(date, if (credit) value else value.negate(), description.trim())
        }

        for (line in lines) {
            when {
                ROWS_START.any { line.startsWith(it) } -> inRows = true
                ROWS_END.any { line.startsWith(it) } -> {
                    inRows = false
                    pendingDate = null
                }
                !inRows -> Unit
                else -> {
                    val newStart = NEW_DATE.find(line)
                    val start = newStart ?: OLD_DATE.find(line)
                    if (start != null) {
                        pendingDate = LocalDate.parse(start.groupValues[1], DATE)
                        pendingNew = newStart != null
                        pendingText.clear()
                        pendingText.append(line.substring(start.range.last + 1).trim())
                    } else if (pendingDate != null) {
                        pendingText.append(" ").append(line)
                    }
                    val date = pendingDate ?: continue
                    val joined = pendingText.toString()
                    // Each layout's own ending: an older row's text ending in "C" isn't a rupee sign.
                    val newEnd = if (pendingNew) NEW_END.matchEntire(joined) else null
                    val oldEnd = if (pendingNew) null else OLD_END.matchEntire(joined)
                    when {
                        newEnd != null -> add(date, newEnd.groupValues[1], newEnd.groupValues[3], credit = newEnd.groupValues[2].isNotEmpty())
                        oldEnd != null -> add(date, oldEnd.groupValues[1], oldEnd.groupValues[2], credit = oldEnd.groupValues[3].isNotEmpty())
                        else -> continue
                    }
                    pendingDate = null
                }
            }
        }

        val summary = summary(lines)
        val debits = rows.filter { it.amount.signum() < 0 }.fold(BigDecimal.ZERO) { s, r -> s - r.amount }
        val credits = rows.filter { it.amount.signum() > 0 }.fold(BigDecimal.ZERO) { s, r -> s + r.amount }
        val addsUp = summary != null &&
            CardStatements.sameAmount(summary.debits, debits) && CardStatements.sameAmount(summary.credits, credits) &&
            // The total due is rounded to the rupee.
            (summary.opening - summary.credits + summary.debits + summary.finance - summary.totalDue).abs() < BigDecimal.ONE

        return ParsedStatement(
            format = "HDFC credit card statement",
            currency = "INR",
            transactions = CardStatements.flagUnlessMatching(rows, addsUp),
            closingBalance = summary?.totalDue,
            closingDate = statementDate(lines),
            fromCard = true,
            issuer = "HDFC",
        )
    }

    private class Summary(val opening: BigDecimal, val credits: BigDecimal, val debits: BigDecimal, val finance: BigDecimal, val totalDue: BigDecimal)

    private fun summary(lines: List<String>): Summary? {
        // 2021–2024: five values after the labels, the last one being the total dues.
        lines.indexOfFirst { it == "Charges Total Dues" }.takeIf { it >= 0 }?.let { i ->
            val values = lines.getOrNull(i + 1)?.split(" ")?.takeIf { v -> v.size == 5 && v.all { AMOUNT_ONLY.matches(it) } }
            return values?.map(::amount)?.let { (opening, credits, debits, finance, total) -> Summary(opening, credits, debits, finance, total) }
        }
        // 2026: four values after the labels; the total due is at the top.
        val start = lines.indexOfFirst { it.startsWith("PREVIOUS STATEMENT DUES") }.takeIf { it >= 0 } ?: return null
        val values = lines.drop(start + 1).take(5).firstNotNullOfOrNull { line ->
            line.split(" ").takeIf { v -> v.size == 4 && v.all { RUPEES.matches(it) } }
        } ?: return null
        val total = lines.indexOfFirst { it == "TOTAL AMOUNT DUE" }.takeIf { it >= 0 }
            ?.let { lines.getOrNull(it + 1) }?.takeIf { RUPEES.matches(it) } ?: return null
        val (opening, credits, debits, finance) = values.map(::rupees)
        return Summary(opening, credits, debits, finance, rupees(total))
    }

    private fun statementDate(lines: List<String>): LocalDate? =
        lines.firstNotNullOfOrNull { OLD_STATEMENT_DATE.find(it) }?.let { LocalDate.parse(it.groupValues[1], DATE) }
            ?: lines.firstNotNullOfOrNull { BILLING_PERIOD.matchEntire(it) }?.let { LocalDate.parse(it.groupValues[1], LONG_DATE) }

    /** "1,23,456.78" (Indian grouping). */
    private fun amount(text: String): BigDecimal = BigDecimal(text.replace(",", ""))

    /** "C1,23,456.78" (the rupee sign comes out as "C"). */
    private fun rupees(text: String): BigDecimal = amount(text.trimStart('C', '₹').trim())

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH)

        /** The headers that start a transaction table, in either layout. */
        val ROWS_START = listOf("Domestic Transactions", "International Transactions", "DATE & TIME TRANSACTION DESCRIPTION")

        /** What follows a table: the reward points, the page footer, the EMI offer. */
        val ROWS_END = listOf("Reward Points Summary", "Rewards Program Points Summary", "Page ", "Eligible for EMI", "* Note")

        const val AMOUNT = """([\d,]+\.\d{2})"""
        val AMOUNT_ONLY = Regex("^$AMOUNT$")
        val RUPEES = Regex("""^[C₹]\s?$AMOUNT$""")

        /** 2026 rows: "dd/MM/yyyy| HH:mm" (international ones: "dd/MM/yyyy | HH:mm"). */
        val NEW_DATE = Regex("""^(\d{2}/\d{2}/\d{4}) ?\|\s*\d{2}:\d{2}(?:\s|$)""")

        /** 2021–2024 rows: "dd/MM/yyyy" and maybe "HH:mm:ss". */
        val OLD_DATE = Regex("""^(\d{2}/\d{2}/\d{4})(?: \d{2}:\d{2}:\d{2})?(?:\s|$)""")

        /** 2026 row end: text, maybe "+ points", maybe "+" (a credit), then "C amount" and the "l" of the insights column. */
        val NEW_END = Regex("""^(.*?)(?:\s*\+\s*[\d,]+)?(\s*\+)?\s*[C₹]\s?$AMOUNT(?:\s+l)?$""")

        /** 2021–2024 row end: text, maybe the reward points, then the amount and "Cr" for a credit. */
        val OLD_END = Regex("""^(.*?)(?:\s+-?[\d,]+)?\s+$AMOUNT( Cr)?$""")

        val OLD_STATEMENT_DATE = Regex("""Statement Date:\s*(\d{2}/\d{2}/\d{4})""")
        val BILLING_PERIOD = Regex("""^\d{2} [A-Za-z]{3}, \d{4} - (\d{2} [A-Za-z]{3}, \d{4})$""")
    }
}
