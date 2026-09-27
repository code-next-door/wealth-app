package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class StatementHistoryTest {

    private fun point(y: Int, m: Int, d: Int, amount: String, units: String? = null) =
        StatementHistory.Point(LocalDate.of(y, m, d), BigDecimal(amount), units?.let(::BigDecimal))

    @Test
    fun accountStatementGivesEachMonthEndAndTheClosingBalance() {
        val points = StatementHistory.points(HdfcStatementParser().parse(TestStatements.hdfc()))
        assertEquals(
            listOf(point(2025, 4, 30, "99220.50"), point(2025, 5, 31, "76720.50"), point(2025, 6, 30, "76420.50")),
            points,
        )
    }

    @Test
    fun aMonthWithoutRowsCarriesTheBalanceOver() {
        val statement = ParsedStatement(
            format = "test",
            currency = "CHF",
            transactions = emptyList(),
            closingBalance = BigDecimal("300"),
            closingDate = LocalDate.of(2025, 3, 31),
            balances = listOf(LocalDate.of(2025, 1, 10) to BigDecimal("100"), LocalDate.of(2025, 3, 5) to BigDecimal("300")),
        )
        assertEquals(
            listOf(point(2025, 1, 31, "100"), point(2025, 2, 28, "100"), point(2025, 3, 31, "300")),
            StatementHistory.points(statement),
        )
    }

    @Test
    fun theClosingLineWinsOnTheLastDay() {
        // In this invented UBS statement the closing line differs from the last row.
        val points = StatementHistory.points(UbsAccountStatementParser().parse(TestStatements.ubsAccount(LocalDate.of(2026, 3, 31))))
        assertEquals(listOf(point(2026, 3, 31, "14215.55")), points)
    }

    @Test
    fun cardAndShareStatementsGiveTheirClosingDay() {
        assertEquals(
            listOf(point(2026, 3, 10, "1356.95")),
            StatementHistory.points(SwisscardStatementParser().parse(TestStatements.swisscard(LocalDate.of(2026, 3, 10)))),
        )
        assertEquals(
            listOf(point(2026, 3, 31, "25.50", units = "112.500")),
            StatementHistory.points(MorganStanleyStatementParser().parse(TestStatements.morganStanley(LocalDate.of(2026, 3, 31)))),
        )
    }
}
