package io.github.codenextdoor.wealth.imports

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** The balance history a statement gives its account, for building history from old statements. */
object StatementHistory {

    /** A balance (the cash, for share accounts, beside [units] shares) on [date], as the statement shows it. */
    data class Point(val date: LocalDate, val amount: BigDecimal, val units: BigDecimal? = null)

    /**
     * Share account statements: what's held on the closing day. Statements
     * with a running balance: the balance at the end of every month they
     * cover (carried over months without rows) and on the closing day. Others
     * (credit cards): the closing balance.
     */
    fun points(statement: ParsedStatement): List<Point> {
        val closingDate = statement.closingDate
        val closing = statement.closingBalance
        statement.holdings?.let { holdings ->
            return if (closingDate == null) emptyList() else listOf(Point(closingDate, holdings.cash, holdings.units))
        }
        val balances = statement.balances.sortedBy { it.first }
        if (balances.isEmpty()) {
            return if (closing != null && closingDate != null) listOf(Point(closingDate, closing)) else emptyList()
        }
        val end = listOfNotNull(closingDate, balances.last().first).max()
        val points = generateSequence(YearMonth.from(balances.first().first)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }
            .mapNotNull { month ->
                val day = minOf(month.atEndOfMonth(), end)
                balances.lastOrNull { !it.first.isAfter(day) }?.let { Point(day, it.second) }
            }
            .toMutableList()
        // The statement's own closing line is the most reliable number for its last day.
        if (closing != null && closingDate != null) {
            points.removeAll { it.date == closingDate }
            points += Point(closingDate, closing)
        }
        return points.sortedBy { it.date }
    }
}
