package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * UBS account statement (PDF), as text extracted in reading order.
 *
 * A transaction starts with a line "DD.MM.YY <type> <amount> <value date>
 * <balance>"; the lines after it (up to the next transaction, a page footer
 * or the turnover total) are its details: counterparty, address, references.
 *
 * In extracted text debits and credits land in the same place, so the
 * direction comes from the running balance: each row's balance minus the
 * previous one. If that change doesn't equal the row's amount, the row is
 * flagged for the user to check.
 */
class UbsAccountStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("UBS") && OPENING.containsMatchIn(text) && TRANSACTION.containsMatchIn(text)

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val currency = lines.firstNotNullOfOrNull { ACCOUNT_CURRENCY.find(it)?.groupValues?.get(1) }

        var balance: BigDecimal? = null
        var closing: Pair<LocalDate, BigDecimal>? = null
        val transactions = mutableListOf<StatementTransaction>()
        val balances = mutableListOf<Pair<LocalDate, BigDecimal>>()

        // The transaction being read, and whether following lines are its details.
        var date: LocalDate? = null
        var type = ""
        var amount = BigDecimal.ZERO
        var needsCheck = false
        val details = mutableListOf<String>()
        var readingDetails = false

        fun finish() {
            val d = date ?: return
            transactions += StatementTransaction(d, amount, describe(type, details), needsCheck)
            date = null
            details.clear()
        }

        for (line in lines) {
            OPENING.matchEntire(line)?.let { match ->
                balance = parseStatementAmount(match.groupValues[2])
                readingDetails = false
                return@let
            } ?: CLOSING.matchEntire(line)?.let { match ->
                finish()
                closing = parseDate(match.groupValues[1]) to parseStatementAmount(match.groupValues[2])
                readingDetails = false
            } ?: TRANSACTION.matchEntire(line)?.let { match ->
                finish()
                val rowAmount = parseStatementAmount(match.groupValues[3])
                val rowBalance = parseStatementAmount(match.groupValues[5])
                val previous = balance
                val change = previous?.let { rowBalance - it }
                date = parseDate(match.groupValues[1])
                type = match.groupValues[2].trim()
                // Direction from the balance; without an opening balance, assume money out.
                amount = if (change != null && change.signum() > 0) rowAmount else rowAmount.negate()
                needsCheck = change == null || change.abs().compareTo(rowAmount) != 0
                balance = rowBalance
                balances += date!! to rowBalance
                readingDetails = true
            } ?: run {
                when {
                    PAGE_END.any { line.startsWith(it) } || TOTALS.any { line.startsWith(it) } -> {
                        finish()
                        readingDetails = false
                    }
                    readingDetails && date != null -> details += line
                }
            }
        }
        finish()

        return ParsedStatement(
            format = "UBS account statement",
            currency = currency,
            transactions = transactions,
            closingBalance = closing?.second,
            closingDate = closing?.first,
            balances = balances,
        )
    }

    /** Counterparty first (most useful to read and to match), then the booking type and other details. */
    private fun describe(type: String, details: List<String>): String {
        val useful = details.filterNot { line -> NOISE.any { it.matches(line) } }
        return (useful.take(1) + type + useful.drop(1).take(3)).joinToString(" · ")
    }

    private fun parseDate(text: String): LocalDate = LocalDate.parse(text, DATE_FORMAT)

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
        const val DATE = """(\d{2}\.\d{2}\.\d{2})"""
        const val AMOUNT = """(-?\d{1,3}(?:[ '’]\d{3})*\.\d{2})"""

        val TRANSACTION = Regex("""^$DATE (.+?) $AMOUNT $DATE $AMOUNT$""", RegexOption.MULTILINE)
        val OPENING = Regex("""^$DATE (?:Opening balance|Anfangssaldo|Solde initial|Saldo iniziale) $AMOUNT$""", RegexOption.MULTILINE)
        val CLOSING = Regex("""^$DATE (?:Closing balance|Schlusssaldo|Solde final|Saldo finale) $AMOUNT$""")
        val ACCOUNT_CURRENCY = Regex("""^UBS .*(?:account|Konto|compte|conto) ([A-Z]{3})$""")

        /** Lines that end a page; the next page repeats headers until the next transaction. */
        val PAGE_END = listOf("Form without signature", "Formular ohne Unterschrift", "Formulaire sans signature", "Modulo senza firma")
        val TOTALS = listOf("Turnover total", "Umsatztotal", "Total des mouvements", "Totale movimenti")

        /** Detail lines that don't help identify the payment. */
        val NOISE = listOf(
            Regex("""^\d+ (?:times|mal|fois|volte) .*"""), // booking channel summary
            Regex("""^[A-Z]?\d[\d ]{7,}$"""), // payment references
            Regex("""^(?:QRR|SCOR|E-BILL|NON)$"""),
        )
    }
}
