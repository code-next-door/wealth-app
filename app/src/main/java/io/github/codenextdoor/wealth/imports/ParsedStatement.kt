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
)

/** Reads statement text in one bank's layout. */
interface StatementParser {
    /** Whether [text] looks like this parser's layout. */
    fun canParse(text: String): Boolean
    fun parse(text: String): ParsedStatement
}

/** Parses a statement amount such as "1 234.50", "1'234.50" or "-45.30". */
internal fun parseStatementAmount(text: String): BigDecimal =
    BigDecimal(text.replace(" ", "").replace("'", "").replace("’", "").replace(" ", ""))
