package io.github.codenextdoor.wealth.testutil

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Invented statements in real layouts. Never put real statement data here. */
object TestStatements {

    private val short = DateTimeFormatter.ofPattern("dd.MM.yy")

    /** A UBS-layout account statement for the month before [end], in CHF. */
    fun ubsAccount(end: LocalDate): String {
        fun d(daysBefore: Long) = end.minusDays(daysBefore).format(short)
        return """
            UBS Switzerland AG
            UBS personal account CHF
            Jane Example
            Account Statement
            Date Information Debits Credits Value date Balance
            ${d(30)} Opening balance 10 000.00
            ${d(28)} STANDING ORDER 2 000.00 ${d(28)} 8 000.00
            EXAMPLE PROPERTIES AG
            CH 8000 ZURICH
            1 times Standing order domestic
            ${d(25)} PAYNET ORDER 49.90 ${d(25)} 7 950.10
            E-BILL
            SWISSCOM (SCHWEIZ) AG
            1 times E-Banking domestic
            ${d(5)} SALARY PAYMENT 7 500.00 ${d(5)} 15 450.10
            EXAMPLE EMPLOYER GMBH
            1 times Incoming SIC-payment
            ${d(2)} DIRECT DEBIT 1 234.55 ${d(2)} 14 215.55
            UBS SWITZERLAND AG
            C/O UBS CARD CENTER
            CREDIT CARD STATEMENT
            1 times LSV direct debit
            Turnover total 3 284.45 7 500.00
            ${d(0)} Closing balance 14 215.55
        """.trimIndent()
    }

    /** A bank CSV export (UBS-style columns), dated around [end]. */
    fun bankCsv(end: LocalDate): String = """
        Account number:;0000 00000000.01;
        Until:;$end;
        Closing balance:;2345.60;
        Valued in:;CHF;

        Trade date;Trade time;Booking date;Value date;Currency;Debit;Credit;Individual amount;Balance;Transaction no.;Description1;Description2;Description3;Footnotes;
        ${end.minusDays(1)};;${end.minusDays(1)};${end.minusDays(1)};CHF;-45.30;;;2345.60;1;"COOP-4521 ZUERICH";"Debit card payment";;;
        ${end.minusDays(3)};;${end.minusDays(3)};${end.minusDays(3)};CHF;-88.00;;;2390.90;2;"SBB CFF FFS";"Debit card payment";;;
        ${end.minusDays(4)};;${end.minusDays(4)};${end.minusDays(4)};CHF;;"100.00";;2478.90;3;"EXAMPLE REFUND";"Credit";;;
    """.trimIndent()
}
