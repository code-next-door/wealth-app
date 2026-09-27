package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented statement in the HDFC layout (as Android extracts it); no real data. */
class HdfcStatementParserTest {

    private val statement = TestStatements.hdfc()
    private val parser = HdfcStatementParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(statement))
        assertTrue(ImportViewModel.PDF_PARSERS.filter { it.canParse(statement) }.single() is HdfcStatementParser)
        assertFalse(parser.canParse(TestStatements.ubsAccount(LocalDate.of(2025, 6, 30))))
    }

    @Test
    fun readsRowsWrappedBeforeAndAfterTheAmount() {
        val parsed = parser.parse(statement)
        assertEquals("INR", parsed.currency)
        val rows = parsed.transactions
        assertEquals(6, rows.size)
        assertEquals(LocalDate.of(2025, 4, 20), rows[2].date)
        // The last wrapped line comes after a page header.
        assertEquals("UPI-UBER INDIA SYSTEMS P-UBERRIDES@HDFCB ANK-HDFC0000000-000000000000-UBERRIDE EXTRA-WRAPPED", rows[2].description)
        assertEquals(BigDecimal("-329.50"), rows[2].amount)
        // Page headers aren't descriptions; a wrapped line after one still is.
        assertTrue(rows[2].description.none { it == ':' })
        assertEquals("UPI-GOOGLE PLAY-PLAYSTORE@AXISBANK-UTIB0 000000-000000000000-MANDATEEXECUTE", rows[5].description)
        assertEquals("IMPS-000000000000-JOHN EXAMPLE-ICIC-XX XXXXXX0000-JOHN", rows[4].description)
    }

    @Test
    fun directionComesFromTheBalanceAndTheFirstRowIsFlagged() {
        val rows = parser.parse(statement).transactions
        assertTrue(rows.first().needsCheck) // no opening balance to compare with
        assertTrue(rows.drop(1).none { it.needsCheck })
        assertEquals(BigDecimal("-450.00"), rows[1].amount)
        assertEquals(BigDecimal("-20000.00"), rows[3].amount)
    }

    @Test
    fun runningBalancesAndClosing() {
        val parsed = parser.parse(statement)
        assertEquals(6, parsed.balances.size)
        assertEquals(LocalDate.of(2025, 4, 2) to BigDecimal("100000.00"), parsed.balances.first())
        assertEquals(BigDecimal("76420.50"), parsed.closingBalance)
        assertEquals(LocalDate.of(2025, 6, 30), parsed.closingDate) // "Statement From ... To"
    }
}
