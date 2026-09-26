package io.github.codenextdoor.wealth.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Uses an invented statement in the UBS layout; no real data. */
class UbsAccountStatementParserTest {

    private val statement = """
        UBS Switzerland AG
        UBS personal account CHF
        IBAN CH00 0000 0000 0000 0000 X
        Jane Example
        Account Statement
        01.03.2026 - 31.03.2026 / Monthly: number 03 Produced on 1 April 2026
        Your account at a glance Debits Credits Balance
        Opening balance 10 000.00
        Date Information Debits Credits Value date Balance
        28.02.26 Opening balance 10 000.00
        02.03.26 STANDING ORDER 2 000.00 02.03.26 8 000.00
        EXAMPLE PROPERTIES AG
        CH 8000 ZURICH
        1 times Standing order domestic
        05.03.26 PAYNET ORDER 49.90 05.03.26 7 950.10
        E-BILL
        EXAMPLE TELECOM AG
        CH ZURICH 8000
        QRR
        000000000000000000000000001
        1 times E-Banking domestic
        Form without signature Page 1 / 2
        ABC12 / 000000 / XYZ 01.04.2026
        UBS personal account CHF
        Jane Example Account Statement 01.03.2026-31.03.2026
        Date Information Debits Credits Value date Balance
        25.03.26 SALARY PAYMENT 7 500.00 25.03.26 15 450.10
        EXAMPLE EMPLOYER GMBH
        1 times Incoming SIC-payment
        28.03.26 DIRECT DEBIT 1 234.55 28.03.26 14 215.55
        UBS SWITZERLAND AG
        C/O UBS CARD CENTER
        CREDIT CARD STATEMENT
        1 times LSV direct debit
        31.03.26 BALANCE CLOSING OF SERVICE PRICES 10.00 31.03.26 14 200.00
        Turnover total 3 294.45 7 500.00
        31.03.26 Closing balance 14 205.55
        Balance Overview Not Including Portfolio Assets
    """.trimIndent()

    private val parser = UbsAccountStatementParser()

    @Test
    fun recognizesLayout() {
        assertTrue(parser.canParse(statement))
        assertFalse(parser.canParse("Date;Description;Amount\n2026-03-01;Coffee;-4.50"))
    }

    @Test
    fun readsTransactionsWithDirectionFromBalance() {
        val parsed = parser.parse(statement)
        assertEquals("CHF", parsed.currency)
        assertEquals(5, parsed.transactions.size)

        val rent = parsed.transactions[0]
        assertEquals(LocalDate.of(2026, 3, 2), rent.date)
        assertEquals(0, BigDecimal("-2000.00").compareTo(rent.amount))
        assertEquals("EXAMPLE PROPERTIES AG · STANDING ORDER · CH 8000 ZURICH", rent.description)

        val salary = parsed.transactions[2]
        assertEquals(0, BigDecimal("7500.00").compareTo(salary.amount))
        assertTrue(salary.description.startsWith("EXAMPLE EMPLOYER GMBH"))
    }

    @Test
    fun dropsNoiseAndPageHeadersFromDetails() {
        val bill = parser.parse(statement).transactions[1]
        assertEquals("EXAMPLE TELECOM AG · PAYNET ORDER · CH ZURICH 8000", bill.description)
    }

    @Test
    fun keepsCardBillTextForSkipRules() {
        val cardBill = parser.parse(statement).transactions[3]
        assertTrue(cardBill.description.contains("CARD CENTER"))
        assertTrue(cardBill.description.contains("CREDIT CARD STATEMENT"))
    }

    @Test
    fun flagsRowsThatDontMatchTheBalance() {
        val parsed = parser.parse(statement)
        assertFalse(parsed.transactions.take(4).any { it.needsCheck })
        // 14 215.55 -> 14 200.00 is a change of 15.55, but the row says 10.00.
        assertTrue(parsed.transactions[4].needsCheck)
    }

    @Test
    fun readsClosingBalance() {
        val parsed = parser.parse(statement)
        assertEquals(LocalDate.of(2026, 3, 31), parsed.closingDate)
        assertEquals(0, BigDecimal("14205.55").compareTo(parsed.closingBalance))
    }
}
