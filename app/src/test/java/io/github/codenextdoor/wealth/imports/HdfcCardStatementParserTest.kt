package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HdfcCardStatementParserTest {

    private val parser = HdfcCardStatementParser()

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun recognizesBothLayoutsAndNoOtherHdfcStatement() {
        assertTrue(parser.canParse(TestStatements.hdfcCardOld()))
        assertTrue(parser.canParse(TestStatements.hdfcCardNew()))
        assertFalse(parser.canParse(TestStatements.hdfc()))
        assertFalse(parser.canParse(TestStatements.hdfcNetBanking()))
        for (text in listOf(TestStatements.hdfcCardOld(), TestStatements.hdfcCardNew())) {
            assertEquals(parser.javaClass, ImportViewModel.STATEMENT_PARSERS.first { it.canParse(text) }.javaClass)
        }
    }

    @Test
    fun olderLayoutRowsWithTheirDirection() {
        val statement = parser.parse(TestStatements.hdfcCardOld())
        val rows = statement.transactions
        assertEquals(listOf("10000.00", "-2500.50", "-199.50", "-1150.00"), rows.map { it.amount.toPlainString() })
        assertTrue(rows.none { it.needsCheck })
        assertEquals(LocalDate.of(2023, 3, 25), rows[2].date)
        // Reward points aren't part of the text.
        assertEquals("EXAMPLE SHOP BANGALORE", rows[1].description)
        assertEquals("VIDEO SITE MUMBAI", rows[2].description)
        assertEquals("AIR EXAMPLE1234567890", rows[3].description)
        assertEquals("IMPS PMT 123456789012 0001 (Ref# 12345678901234567890123)", rows[0].description)
    }

    @Test
    fun olderLayoutBalance() {
        val statement = parser.parse(TestStatements.hdfcCardOld())
        assertTrue(statement.fromCard)
        assertEquals("INR", statement.currency)
        assertAmount("3850.00", statement.closingBalance)
        assertEquals(LocalDate.of(2023, 4, 12), statement.closingDate)
        assertEquals(listOf(LocalDate.of(2023, 4, 12)), StatementHistory.points(statement).map { it.date })
    }

    @Test
    fun newerLayoutRowsAcrossPagesWithTheirDirection() {
        val statement = parser.parse(TestStatements.hdfcCardNew())
        val rows = statement.transactions
        assertEquals(
            listOf("-649.00", "20000.00", "-4000.00", "-7000.00", "-199.00", "-18.00", "-9.00"),
            rows.map { it.amount.toPlainString() },
        )
        assertTrue(rows.none { it.needsCheck })
        assertEquals(LocalDate.of(2026, 6, 18), rows[1].date)
        assertEquals("CREDIT CARD PAYMENTNet Banking (Ref# 12345678901234567890123)", rows[1].description)
        assertEquals("SHOPEXAMPLEBENGALURU", rows[2].description)
        assertEquals("IGST-VPS0000000000000-RATE 18.0 -27 (Ref# ST000000000000000000000)", rows[5].description)
        // International rows have a space before the bar and wrap like the others.
        assertEquals(LocalDate.of(2026, 7, 10), rows[6].date)
        assertEquals("IGST-VPS0000000000000-RATE 18.0 -27 (Ref# ST000000000000000000001)", rows[6].description)
    }

    @Test
    fun newerLayoutBalance() {
        val statement = parser.parse(TestStatements.hdfcCardNew())
        assertTrue(statement.fromCard)
        assertEquals("INR", statement.currency)
        assertAmount("11875.00", statement.closingBalance)
        assertEquals(LocalDate.of(2026, 7, 12), statement.closingDate)
    }

    @Test
    fun rowsThatDontAddUpToTheSummaryAreAllFlagged() {
        assertTrue(parser.parse(TestStatements.hdfcCardOld(firstPurchase = "2,600.50")).transactions.all { it.needsCheck })
        assertTrue(parser.parse(TestStatements.hdfcCardOld(totalDues = "3,900.00")).transactions.all { it.needsCheck })
        assertTrue(parser.parse(TestStatements.hdfcCardNew(firstPurchase = "650.00")).transactions.all { it.needsCheck })
        assertTrue(parser.parse(TestStatements.hdfcCardNew(debits = "11,900.00")).transactions.all { it.needsCheck })
        assertTrue(parser.parse(TestStatements.hdfcCardNew(totalDue = "11,877.00")).transactions.all { it.needsCheck })
    }

    @Test
    fun theTotalDueIsRoundedToTheRupee() {
        // Purchases with paise: the summary has them exactly, the total due is a whole rupee amount.
        val statement = parser.parse(TestStatements.hdfcCardNew(firstPurchase = "649.40", debits = "11,875.40", totalDue = "11,875.00"))
        assertTrue(statement.transactions.none { it.needsCheck })
        assertAmount("11875.00", statement.closingBalance)
    }
}
