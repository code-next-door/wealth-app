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

    /** Three months: SIPs in May and June before July's, in the first fund. */
    private fun threeMonths(mayBalance: String = "1,060.000") = TestStatements.mutualFundCas(from = "01-May-2026").replace(
        "Opening Unit Balance: 1,124.123\n",
        "Opening Unit Balance: 1,000.000\n" +
            "05-May-2026 Purchase - SIP 2,400.00 60.000 40.0000 $mayBalance\n" +
            "*** Stamp Duty *** 0.12\n" +
            "05-Jun-2026 Purchase - SIP 2,693.17 64.123 42.0000 1,124.123\n" +
            // A rejected instalment: units printed, but the balance doesn't change.
            "10-Jun-2026 SIP Purchase Rejected 2,693.17 64.123 42.0000 1,124.123\n",
    )

    @Test
    fun aLongerStatementGivesMonthEndEstimatesAndTheExactClosing() {
        val statement = parser.parse(threeMonths())
        assertFalse(statement.valueNeedsCheck)
        assertTrue(statement.balancesEstimated)
        val points = StatementHistory.points(statement)
        // 31 May: 1,060 units × 40.00 (May's NAV) + the other funds at their only NAV (closing).
        // 30 Jun: 1,124.123 × 42.00 = 47,213.17 + 30,125.00 + 10,123.40. 31 Jul: the statement's own total.
        assertEquals(listOf(LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 31)), points.map { it.date })
        assertEquals(listOf("82648.40", "87461.57", "96642.06").map(::BigDecimal), points.map { it.amount })
        assertEquals(listOf(true, true, false), points.map { it.estimated })
    }

    @Test
    fun unitsThatDontAddUpGiveNoEstimatesOnlyTheClosing() {
        val statement = parser.parse(threeMonths(mayBalance = "1,070.000"))
        assertTrue(statement.valueNeedsCheck)
        assertEquals(listOf(LocalDate.of(2026, 7, 31)), StatementHistory.points(statement).map { it.date })
        assertEquals(0, BigDecimal("96642.06").compareTo(statement.closingBalance))
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
