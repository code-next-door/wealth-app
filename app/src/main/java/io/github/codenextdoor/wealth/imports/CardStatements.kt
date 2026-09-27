package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pieces shared by the credit card statement readers. */
internal object CardStatements {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    const val DATE = """(\d{2}\.\d{2}\.\d{4})"""

    /** "1'234.50", with an optional minus before or after for credits. */
    const val AMOUNT = """(-?\d{1,3}(?:['’]\d{3})*\.\d{2}-?)"""

    val AMOUNT_ONLY = Regex("^$AMOUNT$")

    fun date(text: String): LocalDate = LocalDate.parse(text, DATE_FORMAT)

    /** The amount as printed; a trailing minus counts like a leading one. */
    fun amount(text: String): BigDecimal =
        if (text.endsWith("-")) parseStatementAmount(text.dropLast(1)).negate() else parseStatementAmount(text)

    /** Flags every row when the rows don't add up to what the statement says they total. */
    fun flagUnlessMatching(rows: List<StatementTransaction>, matches: Boolean): List<StatementTransaction> =
        if (matches) rows else rows.map { it.copy(needsCheck = true) }

    fun sameAmount(a: BigDecimal?, b: BigDecimal?): Boolean = a != null && b != null && a.compareTo(b) == 0
}
