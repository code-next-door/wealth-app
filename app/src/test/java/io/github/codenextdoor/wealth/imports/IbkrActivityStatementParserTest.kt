package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented activity statement in the Interactive Brokers layout; no real data. */
class IbkrActivityStatementParserTest {

    private val statement = TestStatements.ibkr()
    private val parser = IbkrActivityStatementParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(statement))
        assertTrue(ImportViewModel.PDF_PARSERS.filter { it.canParse(statement) }.single() is IbkrActivityStatementParser)
    }

    @Test
    fun readsTheAccountValueAtTheStartAndEnd() {
        val parsed = parser.parse(statement)
        assertEquals("CHF", parsed.currency)
        assertEquals(BigDecimal("1000.00"), parsed.openingBalance)
        assertEquals(LocalDate.of(2024, 12, 31), parsed.openingDate)
        assertEquals(BigDecimal("12345.60"), parsed.closingBalance)
        assertEquals(LocalDate.of(2025, 12, 31), parsed.closingDate)
        assertTrue(parsed.transactions.isEmpty()) // trades and deposits aren't spending
    }

    @Test
    fun historyHasTheStartAndEndOnly() {
        val points = StatementHistory.points(parser.parse(statement))
        assertEquals(
            listOf(
                StatementHistory.Point(LocalDate.of(2024, 12, 31), BigDecimal("1000.00")),
                StatementHistory.Point(LocalDate.of(2025, 12, 31), BigDecimal("12345.60")),
            ),
            points,
        )
    }
}
