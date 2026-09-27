package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented report in the UBS e-banking card transactions layout; no real data. */
class UbsCardTransactionsParserTest {

    private val report = TestStatements.ubsCardTransactions()
    private val parser = UbsCardTransactionsParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(report))
        assertTrue(ImportViewModel.STATEMENT_PARSERS.filter { it.canParse(report) }.single() is UbsCardTransactionsParser)
        assertTrue(!parser.canParse(TestStatements.ubsCard(LocalDate.of(2026, 3, 28))))
    }

    @Test
    fun readsPurchasesPaymentsAndForeignAmountsInFrancs() {
        val parsed = parser.parse(report)
        assertEquals("CHF", parsed.currency)
        assertTrue(parsed.fromCard)
        val rows = parsed.transactions
        assertEquals(5, rows.size)
        assertTrue(rows.none { it.needsCheck })
        assertEquals(LocalDate.of(2026, 1, 5), rows[0].date)
        assertEquals("TWINT * EXAMPLE PIZZA Zurich · Restaurants", rows[0].description)
        assertEquals(BigDecimal("-42.50"), rows[0].amount)
        assertEquals(BigDecimal("-18.11"), rows[1].amount) // the francs, not the dollars
        assertEquals("DIRECT DEBIT", rows[2].description)
        assertEquals(BigDecimal("300.00"), rows[2].amount) // the bill paid from the bank: money in
        assertNull(parsed.closingBalance) // the report shows no balance
    }

    @Test
    fun anUnrecognizedCreditBreaksTheTotalsAndFlagsTheRows() {
        // Say the last purchase was really a refund: the credit total no longer matches.
        val refund = report.replace("CHF 409.70 300.00 109.70", "CHF 69.70 640.00 -570.30")
        assertTrue(parser.parse(refund).transactions.all { it.needsCheck })
    }

    @Test
    fun aRefundMarkedAsInTheCreditColumnIsMoneyIn() {
        // Reading the PDF marks amounts printed under "Credit" (see NativePdfText.CreditMarker).
        val refund = report
            .replace("CHF 340.00 16.03.2026", "CHF 340.00 16.03.2026${CreditColumn.MARK}")
            .replace("CHF 409.70 300.00 109.70", "CHF 69.70 640.00 -570.30")
        val rows = parser.parse(refund).transactions
        assertTrue(rows.none { it.needsCheck })
        assertEquals(BigDecimal("340.00"), rows.last().amount)
    }
}
