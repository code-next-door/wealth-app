package io.github.codenextdoor.wealth.testutil

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Invented statements in real layouts. Never put real statement data here. */
object TestStatements {

    private val short = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val long = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * A Morgan Stanley StockPlan Connect quarterly statement ending [end], as
     * Android extracts it: 112.5 shares at 160.00 plus 25.50 cash = 18,025.50.
     */
    fun morganStanley(end: LocalDate): String {
        val us = DateTimeFormatter.ofPattern("M/d/yy")
        val start = end.minusMonths(3).plusDays(1)
        return """
            STATEMENT For the Period January 1 — March 31, 2026
            Morgan Stanley Smith Barney LLC. Member SIPC.
            Jane Example
            Examplestrasse 1
            Zurich
            Plan Details:
            Plan Number: 000X
            Company Name: Example Corp.
            Issuer Description: EXAMPLE CORP CL C
            Account Number: MS00000000
            Share Purchase and Holdings Summary
            Opening Value
            (as of ${start.format(us)})
            Closing Value
            (as of ${end.format(us)})
            Number of Shares 100.000 112.500
            Share Price $150.0000 $160.0000
            Share Value $15,000.00 $18,000.00
            Cash Value $10.00 $25.50
            Net Unsettled Cash $0.00 $0.00
            Total Account Value $15,010.00 $18,025.50
            The quarter-end market closing price is utilized to calculate the Share Value.
            SHARE PURCHASE AND HOLDINGS
            Transaction Date Activity Type Quantity Price
            ${start.plusDays(24).format(us)} Release 4.167 $155.0000
            ${start.plusDays(40).format(us)} Dividend Credit $10.00 $10.00
            ${start.plusDays(40).format(us)} Withholding Tax (1.50)
            ${start.plusDays(55).format(us)} Release 4.167 155.0000
            Sell Transactions are provided as of trade date.
        """.trimIndent()
    }

    /**
     * A Swisscard statement dated [end], as Android extracts the text: two cards,
     * a payment, a refund, a foreign purchase over three lines, a page break.
     * Purchases 1'356.95 in total; balance 1'200.00 → 1'356.95.
     */
    fun swisscard(end: LocalDate): String {
        fun d(daysBefore: Long) = end.minusDays(daysBefore).format(long)
        return """
            Issued by
            Swisscard AECS GmbH
            MS. JANE EXAMPLE
            EXAMPLESTRASSE 1
            8000 ZÜRICH
            Please turn over – page 1/2
            Swisscard AECS GmbH
            P.O. Box 227
            CH-8810 Horgen
            Account number 0000 0000 000
            Spending limit CHF 5'000.00
            Statement date ${end.format(long)}
            Payable until ${end.plusDays(21).format(long)}
            Account holder JANE EXAMPLE
            Statement
            Cashback Cards
            Previous balance Your payments Total new transactions New balance in our
            favor
            Minimum
            payment
            CHF 1'200.00 CHF 1'200.00 CHF 1'356.95 CHF 1'356.95 CHF 50.00
            It’s up to you whether you want to pay the amount shown under "Minimum payment" or perhaps pay off more.
            Date Transaction Amount in CHF
            Your payments
            ${d(23)} YOUR PAYMENT – THANK YOU 1'200.00
            New transactions
            0000 00XXXX X0000 JANE EXAMPLE Cashback American Express
            ${d(26)} MIGROS EXAMPLE, ZÜRICH 45.30
            ${d(24)} UBER EATS, HTTPS://HELP. 32.50
            ${d(18)} EXAMPLE SHOP, ZÜRICH -20.00
            Subtotal cardholder balances 57.80
            Page 1/2
            Balance carried fwd
            ${d(16)} COOP-1234 ZH EXAMPLE, ZÜRICH 1'000.00
            ${d(14)} EXAMPLE STORE, NEW YORK
            USD 110.00, +2.5% handling fee (Foreign transaction)
            99.15
            ${d(12)} JUICE BAR EXAMPLE, ZÜRICH 12.00
            Total transactions JANE EXAMPLE 1'168.95
            0000 00XX XXXX 0000 JOHN EXAMPLE Cashback Visa
            ${d(10)} EXAMPLE GYM, ZÜRICH 150.00
            ${d(8)} EXAMPLE HAIRDRESSER, ZÜRICH 38.00
            Total transactions JOHN EXAMPLE 188.00
            188.00
            Total new transactions 1'356.95
            Cashback overview
            Your cashback level as of statement date ${end.format(long)}
            CHF 12.40
            Bank account details: UBS Switzerland AG, CH-8098 Zurich, IBAN: CH00 0000 0000 0000 0000 0
            Receipt
            Account / Payable to
            CH00 0000 0000 0000 0000 0
        """.trimIndent()
    }

    /**
     * A UBS credit card statement for the period ending [end], as Android extracts
     * the text: each purchase is date + merchant, merchant category, booking date,
     * amount. Purchases 386.35 in total, including a refund and a page break.
     */
    fun ubsCard(end: LocalDate): String {
        fun d(daysBefore: Long) = end.minusDays(daysBefore).format(long)
        return """
            Billing statement CHF
            Booking date Detail Amount CHF
            ${d(25)} Amount of last statement 812.40
            ${d(23)} Direct debit of ${d(23)} -812.40
            ${d(0)} Card total JANE EXAMPLE, UBS Visa Card Platinum 386.35
            ${d(0)} Statement amount 386.35
            The invoice amount will be debited by ${end.plusDays(23).format(long)} directly from the account
            CH00 0000 0000 0000 0000 0.
            Statement check
            UBS Switzerland AG
            Flughofstrasse 35
            P.O. Box
            8152 Glattbrugg
            Card account 0000 0000 0000 0000
            Account limit CHF 10'000
            Account holder JANE EXAMPLE
            Billing period ${d(24)} - ${d(0)}
            Date of invoice ${end.plusDays(1).format(long)}
            Page 1 of 3
            ab
            MS
            JANE EXAMPLE
            UBS Switzerland AG
            Flughofstrasse 35
            JANE EXAMPLE Date of invoice ${end.plusDays(1).format(long)}
            Card account 0000 0000 0000 0000 Page 2 of 3
            Detail statement
            Booking date Detail Amount CHF
            JANE EXAMPLE, UBS Visa Card Platinum, 0000 00XX XXXX 0000
            ${d(23)} TWINT * EXAMPLE PIZZA GmbH Zürich
            Caterers
            ${d(22)}
            42.50
            ${d(21)} EXAMPLE.COM/SUBSCRIPTION 000-0000000 USACA
            Camera and photographic supply stores
            ${d(19)}
            19.90
            ${d(19)} 1.75% CHF surcharge abroad
            ${d(19)}
            0.35
            ${d(16)} TWINT * SBB EasyRide Bern
            Transportation Services n.e.c.
            ${d(16)}
            8.60
            ${d(13)} EXAMPLE SHOP Zürich
            Clothing stores
            ${d(12)}
            -25.00
            ab
            UBS Switzerland AG
            Flughofstrasse 35
            Tel. +41-44-000 00 00
            JANE EXAMPLE Date of invoice ${end.plusDays(1).format(long)}
            Card account 0000 0000 0000 0000 Page 3 of 3
            ${d(8)} TWINT * MIGROS EXAMPLE Zürich
            Grocery stores, supermarkets
            ${d(7)}
            340.00
            Card total 386.35
            Foreign currency conversion and surcharge for transactions abroad
        """.trimIndent()
    }

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
