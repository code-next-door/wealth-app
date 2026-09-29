package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ViacStatementParserTest {

    private val parser = ViacStatementParser()

    @Test
    fun recognizesTheReportAndNothingElse() {
        assertTrue(parser.canParse(TestStatements.viac()))
        assertFalse(parser.canParse(TestStatements.mutualFundCas()))
        assertFalse(parser.canParse(TestStatements.ibkr()))
        assertFalse(parser.canParse(TestStatements.zerodhaHoldings()))
        assertEquals(parser.javaClass, ImportViewModel.STATEMENT_PARSERS.first { it.canParse(TestStatements.viac()) }.javaClass)
    }

    @Test
    fun oneBalanceTheTotalOnTheReportingDate() {
        val statement = parser.parse(TestStatements.viac())
        assertEquals(0, BigDecimal("9699.10").compareTo(statement.closingBalance))
        assertEquals(LocalDate.of(2026, 8, 31), statement.closingDate)
        assertEquals("CHF", statement.currency)
        assertTrue(statement.transactions.isEmpty()) // deposits and trades aren't spending
        assertFalse(statement.valueNeedsCheck)
        assertEquals("ch_pillar3a", statement.accountTypeKey)
        assertEquals("VIAC", statement.issuer)
        // Backfill: one point per monthly report.
        assertEquals(listOf(LocalDate.of(2026, 8, 31)), StatementHistory.points(statement).map { it.date })
    }

    @Test
    fun portfoliosThatDontAddUpToTheTotalAreFlagged() {
        val statement = parser.parse(TestStatements.viac(secondBalance = "2'170.00"))
        assertTrue(statement.valueNeedsCheck)
        assertEquals(0, BigDecimal("9699.10").compareTo(statement.closingBalance)) // still the report's total
    }

    @Test
    fun withoutATotalRowThePortfoliosAreAddedAndFlagged() {
        val statement = parser.parse(TestStatements.viac(total = ""))
        assertEquals(0, BigDecimal("9699.10").compareTo(statement.closingBalance))
        assertTrue(statement.valueNeedsCheck)
    }

    @Test
    fun negativeReturnsAndTypographicApostrophesRead() {
        val text = TestStatements.viac(total = "Total 9'056.00 -1.20% -108.70 8’947.30")
            .replace("7'056.00 8.12% 573.10 7'629.10", "7'056.00 -2.50% -178.70 6’877.30")
        val statement = parser.parse(text)
        assertEquals(0, BigDecimal("8947.30").compareTo(statement.closingBalance))
        assertFalse(statement.valueNeedsCheck)
    }

    @Test
    fun anUnreadableDateIsFlagged() {
        val statement = parser.parse(TestStatements.viac(asOf = "end of August"))
        assertNull(statement.closingDate)
        assertTrue(statement.valueNeedsCheck)
    }

    @Test
    fun vestedBenefitsReportsReadButDontClaimToBePillar3a() {
        val statement = parser.parse(TestStatements.viac(mandate = "Vested benefits"))
        assertEquals(0, BigDecimal("9699.10").compareTo(statement.closingBalance))
        assertNull(statement.accountTypeKey)
    }
}
