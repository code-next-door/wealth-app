package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented statement in the Swisscard layout (as Android extracts it); no real data. */
class SwisscardStatementParserTest {

    private val statement = TestStatements.swisscard(LocalDate.of(2026, 3, 10))
    private val parser = SwisscardStatementParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(statement))
        assertFalse(parser.canParse(TestStatements.ubsAccount(LocalDate.of(2026, 3, 31))))
        assertFalse(parser.canParse(TestStatements.ubsCard(LocalDate.of(2026, 3, 28))))
        assertFalse(UbsAccountStatementParser().canParse(statement))
    }

    @Test
    fun readsPaymentsChargesAndCredits() {
        val parsed = parser.parse(statement)
        assertEquals("CHF", parsed.currency)
        assertTrue(parsed.fromCard)
        val rows = parsed.transactions
        assertEquals(9, rows.size)
        assertTrue(rows.none { it.needsCheck })

        // The card payment is money in; purchases are money out; a refund is money in.
        val payment = rows.single { it.description.startsWith("YOUR PAYMENT") }
        assertEquals(BigDecimal("1200.00"), payment.amount)
        assertEquals(LocalDate.of(2026, 2, 15), payment.date)
        assertEquals(BigDecimal("-1000.00"), rows.single { it.description.startsWith("COOP-1234") }.amount)
        assertEquals(BigDecimal("20.00"), rows.single { it.description.startsWith("EXAMPLE SHOP") }.amount)
        // Charges on the second card are read too.
        assertEquals(BigDecimal("-150.00"), rows.single { it.description.startsWith("EXAMPLE GYM") }.amount)
    }

    @Test
    fun foreignPurchaseSpansThreeLines() {
        val foreign = parser.parse(statement).transactions.single { it.description.startsWith("EXAMPLE STORE") }
        assertEquals("EXAMPLE STORE, NEW YORK · USD 110.00", foreign.description)
        assertEquals(BigDecimal("-99.15"), foreign.amount)
        assertEquals(LocalDate.of(2026, 2, 24), foreign.date)
    }

    @Test
    fun newBalanceIsTheClosingBalance() {
        val parsed = parser.parse(statement)
        assertEquals(BigDecimal("1356.95"), parsed.closingBalance)
        assertEquals(LocalDate.of(2026, 3, 10), parsed.closingDate)
    }

    @Test
    fun rowsThatDontAddUpToTheTotalsAreFlagged() {
        // A purchase the reader can't see (e.g. a changed layout) breaks the totals.
        val missingRow = statement.lines().filterNot { it.contains("JUICE BAR") }.joinToString("\n")
        assertTrue(parser.parse(missingRow).transactions.all { it.needsCheck })
    }

    @Test
    fun summaryThatDoesntAddUpGivesNoClosingBalance() {
        val broken = statement.replace("CHF 1'356.95 CHF 50.00", "CHF 9'999.99 CHF 50.00")
        assertNull(parser.parse(broken).closingBalance)
    }
}
