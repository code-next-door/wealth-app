package io.github.codenextdoor.wealth.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Invented exports in common layouts; no real data. */
class CsvStatementParserTest {

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    private val bankStyle = """
        Account number:;0000 00000000.01;
        IBAN:;CH00 0000 0000 0000 0000 0;
        From:;2026-03-01;
        Until:;2026-03-31;
        Opening balance:;1000.00;
        Closing balance:;2345.60;
        Valued in:;CHF;

        Trade date;Trade time;Booking date;Value date;Currency;Debit;Credit;Individual amount;Balance;Transaction no.;Description1;Description2;Description3;Footnotes;
        2026-03-28;;2026-03-28;2026-03-28;CHF;-45.30;;;2345.60;1;"EXAMPLE MARKET 12, ZURICH";"Debit card payment";"Card no. XXXX 0000";;
        2026-03-25;;2026-03-25;2026-03-25;CHF;;"5'000.00";;2390.90;2;"EXAMPLE EMPLOYER GMBH";"Salary";;;
        2026-03-02;;2026-03-02;2026-03-02;CHF;-1'200.00;;;-2609.10;3;"EXAMPLE PROPERTIES AG";"Standing order";;;
    """.trimIndent()

    @Test
    fun readsBankExportWithDebitAndCreditColumns() {
        val rows = CsvReader.read(bankStyle)
        val mapping = CsvStatementParser.guessMapping(rows)!!
        assertEquals(2, mapping.dateColumn) // booking date preferred over trade/value date
        assertEquals(listOf(10, 11, 12), mapping.descriptionColumns)
        assertEquals("yyyy-MM-dd", mapping.datePattern)

        val parsed = CsvStatementParser.parse(rows, mapping, "CHF")
        assertEquals(3, parsed.transactions.size)
        assertAmount("-45.30", parsed.transactions[0].amount)
        assertEquals("EXAMPLE MARKET 12, ZURICH · Debit card payment · Card no. XXXX 0000", parsed.transactions[0].description)
        assertAmount("5000.00", parsed.transactions[1].amount)
        assertAmount("-1200.00", parsed.transactions[2].amount)
        assertAmount("2345.60", parsed.closingBalance)
        assertEquals(LocalDate.of(2026, 3, 31), parsed.closingDate)
    }

    @Test
    fun detectsCurrencyFromColumnOrHeaderSection() {
        val rows = CsvReader.read(bankStyle)
        assertEquals("CHF", CsvStatementParser.parse(rows, CsvStatementParser.guessMapping(rows)!!).currency)
        val meta = CsvReader.read("Valued in:;EUR;\n\nDate;Text;Amount\n2026-01-01;x;-1.00")
        assertEquals("EUR", CsvStatementParser.parse(meta, CsvStatementParser.guessMapping(meta)!!).currency)
    }

    @Test
    fun readsGermanExportWithSingleAmount() {
        val csv = """
            Buchungsdatum;Valuta;Buchungstext;Betrag;Saldo
            31.03.2026;31.03.2026;Beispiel Bäckerei;-12,40;987,60
            15.03.2026;15.03.2026;Rückerstattung;20,00;1000,00
        """.trimIndent()
        val rows = CsvReader.read(csv)
        val mapping = CsvStatementParser.guessMapping(rows)!!
        assertEquals("dd.MM.yyyy", mapping.datePattern)
        val parsed = CsvStatementParser.parse(rows, mapping)
        assertAmount("-12.40", parsed.transactions[0].amount)
        assertAmount("20.00", parsed.transactions[1].amount)
        // No summary lines: the latest row's balance is the closing balance.
        assertAmount("987.60", parsed.closingBalance)
    }

    @Test
    fun cardExportWithPositiveChargesIsFlipped() {
        val csv = """
            Transaction date,Description,Merchant category,Amount,Currency
            03/03/2026,EXAMPLE CAFE,Restaurants,4.50,CHF
            02/03/2026,EXAMPLE AIRLINE,Travel,320.00,CHF
            01/03/2026,PAYMENT THANK YOU,,-500.00,CHF
        """.trimIndent()
        val rows = CsvReader.read(csv)
        val mapping = CsvStatementParser.guessMapping(rows)!!
        assertEquals(false, mapping.negativeIsMoneyOut)
        assertEquals("dd/MM/yyyy", mapping.datePattern)
        val parsed = CsvStatementParser.parse(rows, mapping)
        assertAmount("-4.50", parsed.transactions[0].amount)
        assertAmount("500.00", parsed.transactions[2].amount)
    }

    @Test
    fun parsesQuotesAndDelimiters() {
        assertEquals(';', CsvReader.detectDelimiter("a;b;c\n1;2;3"))
        assertEquals(',', CsvReader.detectDelimiter("a,b,c\n\"x;y\",2,3"))
        assertEquals(listOf(listOf("a \"quoted\" word", "2")), CsvReader.parse("\"a \"\"quoted\"\" word\",2", ','))
    }

    @Test
    fun readsAmountStyles() {
        assertAmount("-1234.50", parseCsvAmount("-1'234.50"))
        assertAmount("-12.50", parseCsvAmount("12.50-"))
        assertAmount("-12.50", parseCsvAmount("(12.50)"))
        assertAmount("1234.50", parseCsvAmount("1.234,50"))
        assertNull(parseCsvAmount(""))
    }

    @Test
    fun noRecognizableHeaderGivesNoMapping() {
        assertNull(CsvStatementParser.guessMapping(CsvReader.read("foo;bar\n1;2")))
        assertNotNull(CsvStatementParser.guessMapping(CsvReader.read("Date;Amount\n2026-01-01;-1.00")))
    }
}
