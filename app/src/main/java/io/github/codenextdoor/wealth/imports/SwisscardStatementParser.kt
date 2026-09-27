package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.imports.CardStatements.AMOUNT
import io.github.codenextdoor.wealth.imports.CardStatements.AMOUNT_ONLY
import io.github.codenextdoor.wealth.imports.CardStatements.DATE
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Swisscard credit card statement (PDF, English), as Android extracts the text.
 *
 * Under "Your payments" and "New transactions" a row is "DD.MM.YYYY <merchant>
 * <amount>". A foreign purchase takes three lines: date and merchant, the
 * original currency and fee, then the amount alone. Payments to the card are
 * money in, purchases money out, and a minus sign marks a refund.
 *
 * The rows must add up to the statement's totals, or they're all flagged for
 * checking. The new balance is only used if the summary adds up (previous
 * balance - payments + new transactions).
 */
class SwisscardStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("Swisscard AECS") && text.lines().any { it.trim() == NEW_TRANSACTIONS }

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<StatementTransaction>()
        var paymentsRead = BigDecimal.ZERO
        var purchasesRead = BigDecimal.ZERO
        var purchasesTotal: BigDecimal? = null

        var inPayments = false
        var inPurchases = false
        // A foreign purchase waiting for its amount line.
        var pending: Pair<LocalDate, String>? = null
        var pendingDetail: String? = null

        fun add(date: LocalDate, description: String, amountText: String) {
            val printed = CardStatements.amount(amountText)
            val amount = if (inPayments) printed else printed.negate()
            if (inPayments) paymentsRead += printed else purchasesRead += printed
            rows += StatementTransaction(date, amount, description)
        }

        for (line in lines) {
            when {
                line == YOUR_PAYMENTS -> {
                    inPayments = true
                    inPurchases = false
                    pending = null
                }
                line == NEW_TRANSACTIONS -> {
                    inPayments = false
                    inPurchases = true
                    pending = null
                }
                line.startsWith(TOTAL_NEW_TRANSACTIONS) -> {
                    purchasesTotal = TRAILING_AMOUNT.find(line)?.let { CardStatements.amount(it.groupValues[1]) }
                    inPayments = false
                    inPurchases = false
                    pending = null
                }
                !inPayments && !inPurchases -> Unit
                else -> {
                    ROW.matchEntire(line)?.let { m ->
                        pending = null
                        add(CardStatements.date(m.groupValues[1]), m.groupValues[2].trim(), m.groupValues[3])
                    } ?: DATED.matchEntire(line)?.let { m ->
                        pending = CardStatements.date(m.groupValues[1]) to m.groupValues[2].trim()
                        pendingDetail = null
                    } ?: AMOUNT_ONLY.matchEntire(line)?.let { m ->
                        pending?.let { (date, merchant) -> add(date, listOfNotNull(merchant, pendingDetail).joinToString(" · "), m.groupValues[1]) }
                        pending = null
                    } ?: run {
                        // "USD 110.00, +2.5% handling fee (Foreign transaction)" -> "USD 110.00"
                        if (pending != null && pendingDetail == null) pendingDetail = line.substringBefore(", ")
                    }
                }
            }
        }

        // Previous balance, payments, new transactions, new balance (then the minimum payment).
        val summary = lines.firstNotNullOfOrNull { SUMMARY.find(it) }?.groupValues?.drop(1)?.map(CardStatements::amount)
        val summaryAddsUp = summary != null && CardStatements.sameAmount(summary[0] - summary[1] + summary[2], summary[3])
        val rowsAddUp = CardStatements.sameAmount(purchasesRead, purchasesTotal) &&
            (summary == null || CardStatements.sameAmount(paymentsRead, summary[1]))

        return ParsedStatement(
            format = "Swisscard statement",
            currency = lines.firstNotNullOfOrNull { CURRENCY.find(it)?.groupValues?.get(1) },
            transactions = CardStatements.flagUnlessMatching(rows, rowsAddUp),
            closingBalance = if (summaryAddsUp) summary!![3] else null,
            closingDate = lines.firstNotNullOfOrNull { STATEMENT_DATE.matchEntire(it)?.groupValues?.get(1) }?.let(CardStatements::date),
            fromCard = true,
        )
    }

    private companion object {
        const val YOUR_PAYMENTS = "Your payments"
        const val NEW_TRANSACTIONS = "New transactions"
        const val TOTAL_NEW_TRANSACTIONS = "Total new transactions"

        val ROW = Regex("^$DATE (.+) $AMOUNT$")
        val DATED = Regex("^$DATE (.+)$")
        val TRAILING_AMOUNT = Regex(" $AMOUNT$")
        val SUMMARY = Regex("^CHF $AMOUNT CHF $AMOUNT CHF $AMOUNT CHF $AMOUNT")
        val CURRENCY = Regex("""^Date Transaction Amount in ([A-Z]{3})$""")
        val STATEMENT_DATE = Regex("^Statement date $DATE$")
    }
}
