package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate

/** One row read from a statement. */
data class StatementTransaction(
    val date: LocalDate,
    /** Money out is negative, money in positive, in the statement's currency. */
    val amount: BigDecimal,
    /** Text used for display and categorization, e.g. "ACME AG · PAYNET ORDER". */
    val description: String,
    /** True when the row's amount doesn't match the change in the running balance. */
    val needsCheck: Boolean = false,
)

data class ParsedStatement(
    /** Human-readable name of the format, e.g. "UBS account statement". */
    val format: String,
    /** ISO code if the statement says, otherwise null. */
    val currency: String?,
    val transactions: List<StatementTransaction>,
    val closingBalance: BigDecimal? = null,
    val closingDate: LocalDate? = null,
    /** A credit card statement: it belongs to a card (liability) account, not a bank account. */
    val fromCard: Boolean = false,
    /** A share account statement: the shares and cash held at [closingDate]. */
    val holdings: Holdings? = null,
    /** The balance after each row, oldest first, when the statement shows a running balance. */
    val balances: List<Pair<LocalDate, BigDecimal>> = emptyList(),
    /** The balance before the statement's period, when it says (and has no running balance). */
    val openingBalance: BigDecimal? = null,
    val openingDate: LocalDate? = null,
)

/** Shares and cash held on a statement's closing day, at that day's [price]. */
data class Holdings(
    val units: BigDecimal,
    val price: BigDecimal,
    val cash: BigDecimal,
    /** Shares × price + cash equals the statement's total. */
    val addsUp: Boolean,
)

/** Reads statement text in one bank's layout. */
interface StatementParser {
    /** Whether [text] looks like this parser's layout. */
    fun canParse(text: String): Boolean
    fun parse(text: String): ParsedStatement

    /** Set for layouts whose debits and credits only differ by column (see [CreditColumn]). */
    val creditColumn: CreditColumn? get() = null
}

/**
 * A layout where money in and money out are only told apart by the column the
 * amount is printed in, which plain text loses. When reading such a PDF,
 * amounts found under the "Credit" header get [MARK] appended to their line.
 */
class CreditColumn(
    /** The table header, e.g. "... Amount Debit Credit Booked": identifies the pages. */
    val header: String,
    /** A line with an amount; group 1 is the text to look for on the page, e.g. "CHF 42.50". */
    val amountLine: Regex,
) {
    companion object {
        const val MARK = " CR"
    }
}

/** Parses a statement amount such as "1 234.50", "1'234.50" or "-45.30". */
internal fun parseStatementAmount(text: String): BigDecimal =
    BigDecimal(text.replace(" ", "").replace("'", "").replace("’", "").replace(" ", ""))
