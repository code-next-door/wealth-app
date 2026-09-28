package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MutualFundCasParserTest {

    private val parser = MutualFundCasParser()

    @Test
    fun recognizesTheStatementAndNothingElse() {
        assertTrue(parser.canParse(TestStatements.mutualFundCas()))
        assertFalse(parser.canParse(TestStatements.zerodhaHoldings()))
        assertFalse(parser.canParse(TestStatements.ibkr()))
        assertEquals(parser.javaClass, ImportViewModel.STATEMENT_PARSERS.first { it.canParse(TestStatements.mutualFundCas()) }.javaClass)
    }

    @Test
    fun oneBalanceTheSumOfEveryFundOnTheLastDay() {
        val statement = parser.parse(TestStatements.mutualFundCas())
        assertEquals(0, BigDecimal("96642.06").compareTo(statement.closingBalance))
        assertEquals(LocalDate.of(2026, 7, 31), statement.closingDate)
        assertEquals("INR", statement.currency)
        assertTrue(statement.transactions.isEmpty()) // buying funds isn't spending
        assertFalse(statement.valueNeedsCheck)
        assertEquals("in_mutual_funds", statement.accountTypeKey)
        // Backfill: one point per monthly file.
        assertEquals(listOf(LocalDate.of(2026, 7, 31)), StatementHistory.points(statement).map { it.date })
    }

    @Test
    fun aFundWhoseValueIsNotUnitsTimesNavIsFlagged() {
        val statement = parser.parse(TestStatements.mutualFundCas(secondValuation = "31,125.00"))
        assertTrue(statement.valueNeedsCheck)
        assertEquals(0, BigDecimal("97642.06").compareTo(statement.closingBalance)) // still the statement's own values
    }

    @Test
    fun aClosingLineItCantReadIsFlagged() {
        val broken = TestStatements.mutualFundCas().replace("Nav as on 31-JUL-2026: INR 120.50", "Nav unknown")
        assertTrue(parser.parse(broken).valueNeedsCheck)
    }

    @Test
    fun aClosingLineSplitOverTwoLinesStillReads() {
        val wrapped = TestStatements.mutualFundCas().replace("INR 120.50 Valuation", "INR 120.50\nValuation")
        val statement = parser.parse(wrapped)
        assertFalse(statement.valueNeedsCheck)
        assertEquals(0, BigDecimal("96642.06").compareTo(statement.closingBalance))
    }

    @Test
    fun fundsWithoutAFolioAreLeftOutAndFlagged() {
        // Demat-held funds (a broker's export already counts them) have no folio number.
        val demat = TestStatements.mutualFundCas().replace("FOLIO NO: 9876544\n", "")
        val statement = parser.parse(demat)
        assertEquals(0, BigDecimal("86518.66").compareTo(statement.closingBalance))
        assertTrue(statement.valueNeedsCheck)
    }
}
