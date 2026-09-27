package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.imports.CardStatements.AMOUNT
import io.github.codenextdoor.wealth.imports.CardStatements.AMOUNT_ONLY
import io.github.codenextdoor.wealth.imports.CardStatements.DATE
import java.math.BigDecimal
import java.time.LocalDate

/**
 * UBS credit card statement (PDF, English), as Android extracts the text.
 *
 * After "Detail statement" each purchase is a block of lines: date and
 * merchant, the merchant's category (not always there), the booking date, and
 * the amount alone. A minus sign marks a refund. Page headers between blocks
 * are skipped. Each card ends with "Card total", which the rows must add up to,
 * or they're all flagged for checking.
 *
 * The first page's "Statement amount" is what's owed: the closing balance.
 */
class UbsCardStatementParser : StatementParser {

    override fun canParse(text: String): Boolean =
        text.contains("UBS") && text.contains("Card account") && text.lines().any { it.trim() == DETAIL_STATEMENT }

    override fun parse(text: String): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<StatementTransaction>()
        var purchasesRead = BigDecimal.ZERO
        var cardTotals = BigDecimal.ZERO
        var cardTotalSeen = false
        var closing: Pair<LocalDate, BigDecimal>? = null

        class Block(val date: LocalDate, val merchant: String) {
            var category: String? = null
            var booked = false
        }
        var inDetail = false
        var block: Block? = null

        fun add(date: LocalDate, description: String, amountText: String) {
            val printed = CardStatements.amount(amountText)
            purchasesRead += printed
            rows += StatementTransaction(date, printed.negate(), description)
        }

        for (line in lines) {
            if (!inDetail) {
                STATEMENT_AMOUNT.matchEntire(line)?.let { m ->
                    closing = CardStatements.date(m.groupValues[1]) to CardStatements.amount(m.groupValues[2])
                }
                inDetail = line == DETAIL_STATEMENT
                continue
            }
            CARD_TOTAL.matchEntire(line)?.let { m ->
                cardTotals += CardStatements.amount(m.groupValues[1])
                cardTotalSeen = true
                block = null
            } ?: DATE_ONLY.matchEntire(line)?.let {
                block?.booked = true
            } ?: AMOUNT_ONLY.matchEntire(line)?.let { m ->
                block?.let { b -> add(b.date, listOfNotNull(b.merchant, b.category).joinToString(" · "), m.groupValues[1]) }
                block = null
            } ?: ROW.matchEntire(line)?.let { m ->
                // In case a purchase's amount ends up on its first line.
                block = null
                add(CardStatements.date(m.groupValues[1]), m.groupValues[2].trim(), m.groupValues[3])
            } ?: DATED.matchEntire(line)?.let { m ->
                block = Block(CardStatements.date(m.groupValues[1]), m.groupValues[2].trim())
            } ?: block?.let { b ->
                // The line right after the merchant is its category; anything later is page furniture.
                if (!b.booked && b.category == null) b.category = line
            }
        }

        return ParsedStatement(
            format = "UBS credit card statement",
            currency = lines.firstNotNullOfOrNull { CURRENCY.matchEntire(it)?.groupValues?.get(1) },
            transactions = CardStatements.flagUnlessMatching(rows, cardTotalSeen && CardStatements.sameAmount(purchasesRead, cardTotals)),
            closingBalance = closing?.second,
            closingDate = closing?.first,
            fromCard = true,
        )
    }

    private companion object {
        const val DETAIL_STATEMENT = "Detail statement"

        val ROW = Regex("^$DATE (.+) $AMOUNT$")
        val DATED = Regex("^$DATE (.+)$")
        val DATE_ONLY = Regex("^$DATE$")
        val CARD_TOTAL = Regex("^Card total $AMOUNT$")
        val STATEMENT_AMOUNT = Regex("^$DATE Statement amount $AMOUNT$")
        val CURRENCY = Regex("""^Billing statement ([A-Z]{3})$""")
    }
}
