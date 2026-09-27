package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented holdings file in the Zerodha layout; no real data. */
class ZerodhaHoldingsParserTest {

    private val parser = ZerodhaHoldingsParser()

    @Test
    fun recognizesOnlyItsLayout() {
        val text = TestStatements.zerodhaHoldings()
        assertTrue(parser.canParse(text))
        assertTrue(ImportViewModel.STATEMENT_PARSERS.filter { it.canParse(text) }.single() is ZerodhaHoldingsParser)
        assertFalse(parser.canParse(TestStatements.ibkr()))
    }

    @Test
    fun theCombinedPresentValueOnTheAsOnDate() {
        val parsed = parser.parse(TestStatements.zerodhaHoldings())
        assertEquals("INR", parsed.currency)
        assertEquals(0, BigDecimal("13650").compareTo(parsed.closingBalance))
        assertEquals(LocalDate.of(2026, 3, 31), parsed.closingDate)
        assertTrue(parsed.transactions.isEmpty())
        assertFalse(parsed.valueNeedsCheck) // 10 × 1000 + 25.5 × 100 + 11 × 100
        assertEquals(listOf(StatementHistory.Point(LocalDate.of(2026, 3, 31), BigDecimal("13650.0000"))), StatementHistory.points(parsed))
    }

    @Test
    fun aValueThatDoesntMatchTheHoldingsIsFlagged() {
        assertTrue(parser.parse(TestStatements.zerodhaHoldings(presentValue = "99999.0000")).valueNeedsCheck)
    }
}
