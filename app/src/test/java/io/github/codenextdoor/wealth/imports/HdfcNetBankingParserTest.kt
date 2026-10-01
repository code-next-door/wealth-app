package io.github.codenextdoor.wealth.imports

import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HdfcNetBankingParserTest {

    private val parser = HdfcNetBankingParser()

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun recognizesThisLayoutAndNotTheOtherHdfcOne() {
        assertTrue(parser.canParse(TestStatements.hdfcNetBanking()))
        assertFalse(parser.canParse(TestStatements.hdfc()))
        assertFalse(HdfcStatementParser().canParse(TestStatements.hdfcNetBanking()))
        assertEquals(parser.javaClass, ImportViewModel.STATEMENT_PARSERS.first { it.canParse(TestStatements.hdfcNetBanking()) }.javaClass)
    }

    @Test
    fun readsEveryRowAcrossPagesWithItsDirection() {
        val statement = parser.parse(TestStatements.hdfcNetBanking())
        val rows = statement.transactions
        assertEquals(5, rows.size)
        assertEquals(listOf("-5000.00", "-1250.50", "-250.00", "200000.00", "1234.00"), rows.map { it.amount.toPlainString() })
        assertTrue(rows.none { it.needsCheck })
        assertEquals(LocalDate.of(2023, 3, 5), rows[1].date)
        // Wrapped narrations join without a space (HDFC wraps mid-word); the reference is dropped.
        assertEquals("UPI-EXAMPLE SHOP-SHOP@AXISBANK-UTIB0000001-123456789012-GROCERIES", rows[1].description)
        assertEquals("UPI-COFFEE PLACE-COFFEE@HDFCBANK-HDFC0000001-999", rows[2].description)
        assertEquals("ACH D- EXAMPLE FUND P-ABC123", rows[0].description)
        assertEquals("CREDIT INTEREST CAPITALISED", rows[4].description) // no reference on this one
    }

    @Test
    fun balancesForHistory() {
        val statement = parser.parse(TestStatements.hdfcNetBanking())
        assertEquals("INR", statement.currency)
        assertAmount("294733.50", statement.closingBalance)
        assertEquals(LocalDate.of(2023, 3, 31), statement.closingDate)
        assertAmount("100000.00", statement.openingBalance)
        assertEquals(LocalDate.of(2023, 2, 28), statement.openingDate)
        assertEquals(5, statement.balances.size)
        assertEquals(listOf(LocalDate.of(2023, 3, 31)), StatementHistory.points(statement).map { it.date })
    }

    @Test
    fun rowsThatDontAddUpToTheTotalsAreAllFlagged() {
        // A misread amount: the running balance and the debit total no longer agree.
        val statement = parser.parse(TestStatements.hdfcNetBanking(firstWithdrawal = "5,100.00"))
        assertTrue(statement.transactions.all { it.needsCheck })
        val wrongTotal = parser.parse(TestStatements.hdfcNetBanking(debitsTotal = "6,000.00"))
        assertTrue(wrongTotal.transactions.all { it.needsCheck })
    }

    @Test
    fun aMonthWithoutRowsStillGivesItsBalances() {
        val statement = parser.parse(TestStatements.hdfcNetBanking(rows = false))
        assertTrue(statement.transactions.isEmpty())
        assertEquals(
            listOf(LocalDate.of(2023, 2, 28), LocalDate.of(2023, 3, 31)),
            StatementHistory.points(statement).map { it.date },
        )
    }

    @Test
    fun aBalanceBelowZeroIsReadAndAZeroRowIsSkipped() {
        // A debit takes the balance below zero for a while; a failed payment shows 0.00 both ways.
        val text = TestStatements.hdfcNetBanking()
            .replace(
                "02/03/2023 ACH D- EXAMPLE FUND P-ABC123 000012345678 02/03/2023 5,000.00 0.00 95,000.00",
                "02/03/2023 ACH D- BIG PAYMENT 000012345670 02/03/2023 1,05,000.00 0.00 -5,000.00\n" +
                    "02/03/2023 UPI-FAILED PAYMENT 000012345671 02/03/2023 0.00 0.00 -5,000.00\n" +
                    "02/03/2023 NEFT CR-OWN TRANSFER 000012345672 02/03/2023 0.00 1,00,000.00 95,000.00",
            )
            .replace("Debits\n6,500.50", "Debits\n1,06,500.50")
            .replace("Credits\n2,01,234.00", "Credits\n3,01,234.00")
        val statement = parser.parse(text)
        assertEquals(6, statement.transactions.size) // the zero row isn't a transaction
        assertTrue(statement.transactions.none { it.needsCheck })
        assertAmount("-5000.00", statement.balances.first().second)
        assertAmount("-105000.00", statement.transactions.first().amount)
    }
}
