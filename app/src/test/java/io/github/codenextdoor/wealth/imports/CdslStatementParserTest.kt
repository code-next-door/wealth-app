package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CdslStatementParserTest {

    private val parser = CdslStatementParser()
    private fun d(day: Int, month: Int) = LocalDate.of(2026, month, day)
    private fun n(text: String) = BigDecimal(text)

    @Test
    fun recognizesItAndNothingElse() {
        assertTrue(parser.canParse(TestStatements.cdsl()))
        assertTrue(parser.canParse(TestStatements.cdslEmpty()))
        assertFalse(parser.canParse(TestStatements.mutualFundCas()))
        assertFalse(parser.canParse(TestStatements.zerodhaHoldings()))
        assertEquals(parser.javaClass, ImportViewModel.STATEMENT_PARSERS.first { it.canParse(TestStatements.cdsl()) }.javaClass)
    }

    @Test
    fun theRealTotalOnTheHoldingsDayAndEachSharesQuantities() {
        val statement = parser.parse(TestStatements.cdsl())
        assertEquals(0, n("3300.00").compareTo(statement.closingBalance))
        assertEquals(d(31, 3), statement.closingDate)
        assertEquals("INR", statement.currency)
        assertEquals("in_stocks", statement.accountTypeKey)
        assertTrue(statement.transactions.isEmpty())
        assertFalse(statement.valueNeedsCheck)
        val demat = statement.demat!!
        assertEquals(d(1, 1), demat.from)
        assertEquals(listOf(d(1, 1) to n("0.000"), d(15, 1) to n("10.000"), d(20, 2) to n("15.000")), demat.quantities.getValue("INE000A01011"))
        // The page break inside Sample Bank's rows doesn't matter.
        assertEquals(listOf(d(1, 1) to n("20.000"), d(10, 3) to n("12.000")), demat.quantities.getValue("INE000B01022"))
        // Prices from the holdings: value ÷ quantity.
        assertEquals(0, n("100").compareTo(demat.prices.getValue("INE000A01011")))
        assertEquals(0, n("150").compareTo(demat.prices.getValue("INE000B01022")))
    }

    @Test
    fun aShareHeldWithoutTransactionsCountsAllPeriod() {
        // CDSL lists only shares with transactions in the period; the holdings show the others
        // (this one at a page break, where its numbers end the ISIN's line).
        val statement = parser.parse(TestStatements.cdsl(untraded = true))
        assertFalse(statement.valueNeedsCheck)
        assertEquals(listOf(d(1, 1) to n("4.000")), statement.demat!!.quantities.getValue("INE000C01033"))
    }

    @Test
    fun quantitiesThatDontReachTheClosingBalanceGiveNoMonthEnds() {
        val statement = parser.parse(TestStatements.cdsl(lastBalance = "16.000"))
        assertTrue(statement.valueNeedsCheck)
        assertNull(statement.demat)
        assertEquals(0, n("3300.00").compareTo(statement.closingBalance)) // the statement's own total still counts
    }

    @Test
    fun holdingsThatDontAddUpToTheValuationAreFlagged() {
        assertTrue(parser.parse(TestStatements.cdsl(sampleBankValue = "1,900.00")).valueNeedsCheck)
    }

    @Test
    fun aPeriodWithNothingHeldGivesNoBalance() {
        val statement = parser.parse(TestStatements.cdslEmpty())
        assertNull(statement.closingBalance)
        assertNull(statement.demat)
        assertTrue(StatementHistory.points(statement).isEmpty())
    }
}
