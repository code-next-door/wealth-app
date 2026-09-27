package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented statement in the Morgan Stanley StockPlan Connect layout; no real data. */
class MorganStanleyStatementParserTest {

    private val end = LocalDate.of(2026, 3, 31)
    private val statement = TestStatements.morganStanley(end)
    private val parser = MorganStanleyStatementParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(statement))
        assertFalse(parser.canParse(TestStatements.ubsAccount(end)))
        assertFalse(parser.canParse(TestStatements.swisscard(end)))
        assertTrue(ImportViewModel.PDF_PARSERS.filter { it.canParse(statement) }.single() is MorganStanleyStatementParser)
    }

    @Test
    fun readsTheClosingHoldings() {
        val parsed = parser.parse(statement)
        val holdings = parsed.holdings!!
        assertEquals(0, BigDecimal("112.5").compareTo(holdings.units))
        assertEquals(0, BigDecimal("160").compareTo(holdings.price))
        assertEquals(0, BigDecimal("25.5").compareTo(holdings.cash))
        assertTrue(holdings.addsUp)
        assertEquals("USD", parsed.currency)
        assertEquals(end, parsed.closingDate)
        assertEquals(0, BigDecimal("18025.5").compareTo(parsed.closingBalance))
        assertTrue(parsed.transactions.isEmpty()) // vests and dividends aren't spending
    }

    @Test
    fun holdingsThatDontAddUpAreFlagged() {
        val broken = statement.replace("Total Account Value $15,010.00 $18,025.50", "Total Account Value $15,010.00 $19,025.50")
        assertFalse(parser.parse(broken).holdings!!.addsUp)
    }
}
