package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented statement in the UBS credit card layout (as Android extracts it); no real data. */
class UbsCardStatementParserTest {

    private val statement = TestStatements.ubsCard(LocalDate.of(2026, 3, 28))
    private val parser = UbsCardStatementParser()

    @Test
    fun recognizesOnlyItsLayout() {
        assertTrue(parser.canParse(statement))
        assertFalse(parser.canParse(TestStatements.ubsAccount(LocalDate.of(2026, 3, 31))))
        assertFalse(parser.canParse(TestStatements.swisscard(LocalDate.of(2026, 3, 10))))
        assertFalse(UbsAccountStatementParser().canParse(statement))
    }

    @Test
    fun readsEachPurchaseBlock() {
        val parsed = parser.parse(statement)
        assertEquals("CHF", parsed.currency)
        assertTrue(parsed.fromCard)
        val rows = parsed.transactions
        assertEquals(6, rows.size)
        assertTrue(rows.none { it.needsCheck })

        val pizza = rows.first()
        assertEquals(LocalDate.of(2026, 3, 5), pizza.date)
        assertEquals("TWINT * EXAMPLE PIZZA GmbH Zürich · Caterers", pizza.description)
        assertEquals(BigDecimal("-42.50"), pizza.amount)
        // The surcharge has no merchant category line.
        assertEquals(BigDecimal("-0.35"), rows.single { it.description.startsWith("1.75% CHF surcharge") }.amount)
        // A refund is money in.
        assertEquals(BigDecimal("25.00"), rows.single { it.description.startsWith("EXAMPLE SHOP") }.amount)
        // The purchase after a page break is still read, without the page's header lines.
        assertEquals("TWINT * MIGROS EXAMPLE Zürich · Grocery stores, supermarkets", rows.last().description)
    }

    @Test
    fun summaryLinesAreNotPurchases() {
        val rows = parser.parse(statement).transactions
        assertTrue(rows.none { it.description.contains("last statement") || it.description.contains("Direct debit") })
    }

    @Test
    fun statementAmountIsTheClosingBalance() {
        val parsed = parser.parse(statement)
        assertEquals(BigDecimal("386.35"), parsed.closingBalance)
        assertEquals(LocalDate.of(2026, 3, 28), parsed.closingDate)
    }

    @Test
    fun rowsThatDontAddUpToTheCardTotalAreFlagged() {
        val missingRow = statement.replace("\n8.60\n", "\n")
        assertTrue(parser.parse(missingRow).transactions.all { it.needsCheck })
    }
}
