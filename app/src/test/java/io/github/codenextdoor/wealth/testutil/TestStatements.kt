package io.github.codenextdoor.wealth.testutil

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Invented statements in real layouts. Never put real statement data here. */
object TestStatements {

    private val short = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val long = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * A UBS e-banking credit card transactions report (Android's text): purchases,
     * a foreign one, a bill payment by direct debit, a page footer. Debits 409.70,
     * credits 300.00, as the totals line says.
     */
    fun ubsCardTransactions(): String = """
        Reports 01.01.2026 - 31.03.2026
        Purchase Booking text Amount Debit Credit Booked
        05.01.2026 TWINT * EXAMPLE PIZZA Zurich CHE
        Restaurants
        0000 00XX XXXX 0000, JANE EXAMPLE
        CHF 42.50 06.01.2026
        10.01.2026 EXAMPLE.COM Subscription USA
        Computer software stores
        Exchange rate on 10.01.2026: 0.8900000000
        Processing fee: 1.75%
        0000 00XX XXXX 0000, JANE EXAMPLE
        USD 20.00 CHF 18.11 12.01.2026
        UBS Switzerland AG
        Flughofstrasse 35
        Displayed in UBS e-banking / Credit cards on 01.04.2026, 09:00:00 MEST
        Page 1/2
        ab
        00000 E 000 01.04.2026 E0N N0 01.04.2026
        25.01.2026 DIRECT DEBIT
        0000 0000 0000, JANE EXAMPLE
        CHF 300.00 25.01.2026
        02.02.2026 TWINT * SBB EasyRide Bern CHE
        Commuter transportation
        0000 00XX XXXX 0000, JANE EXAMPLE
        CHF 9.09 02.02.2026
        15.03.2026 digitec Galaxus (Online) Zurich CHE
        Department stores
        0000 00XX XXXX 0000, JANE EXAMPLE
        CHF 340.00 16.03.2026
        Total per currency Total
        Total card bookings CHF 409.70 300.00 109.70
        UBS Switzerland AG
        Displayed in UBS e-banking / Credit cards on 01.04.2026, 09:00:00 MEST
        Page 2/2
    """.trimIndent()

    /**
     * A Zerodha holdings .xlsx as [io.github.codenextdoor.wealth.imports.XlsxText]
     * gives it: Equity, Mutual Funds and Combined sheets. Combined present value
     * 13,650.00 = 10 × 1,000.00 (stock) + 25.5 × 100.00 (stock) + 11 × 100.00 (fund).
     */
    fun zerodhaHoldings(asOn: String = "2026-03-31", presentValue: String = "13650.0000"): String {
        // Rows start with a tab: column A is empty, as in Zerodha's files.
        val summary = { title: String, value: String ->
            listOf(
                "\tClient ID\tAB0000",
                "\t$title Holdings Statement as on $asOn",
                "\tSummary",
                "\tInvested Value\t12000.0000",
                "\tPresent Value\t$value",
                "\tUnrealized P&L\t1650.0000",
            ).joinToString("\n")
        }
        return listOf(
            "# Sheet: Equity",
            summary("Equity", "12550.0000"),
            "\tSymbol\tISIN\tSector\tQuantity Available\tQuantity Discrepant\tQuantity Long Term\tQuantity Pledged (Margin)\tQuantity Pledged (Loan)\tAverage Price\tPrevious Closing Price\tUnrealized P&L\tUnrealized P&L Pct.",
            "\tEXAMPLEIND\tINE000A00000\tFMCG\t10.0000\t0.0000\t10.0000\t0.0000\t0.0000\t900.0000\t1000.0000\t1000.0000\t11.1111",
            "\tSAMPLECO\tINE000B00000\tIT\t25.5000\t0.0000\t0.0000\t0.0000\t0.0000\t80.0000\t100.0000\t510.0000\t25.0000",
            "# Sheet: Mutual Funds",
            summary("Mutual Funds", "1100.0000"),
            "\tSymbol\tISIN\tInstrument Type\tQuantity Available\tQuantity Discrepant\tQuantity Pledged (Margin)\tQuantity Pledged (Loan)\tAverage Price\tPrevious Closing Price\tUnrealized P&L\tUnrealized P&L Pct.",
            "\tEXAMPLE FLEXI CAP FUND\tINF000C00000\tEquity\t11.0000\t0.0000\t0.0000\t0.0000\t90.0000\t100.0000\t110.0000\t11.1111",
            "# Sheet: Combined",
            summary("Combined", presentValue),
            "\tSymbol\tISIN\tSector\tInstrument Type\tQuantity Available\tQuantity Discrepant\tQuantity Long Term\tQuantity Pledged (Margin)\tQuantity Pledged (Loan)\tAverage Price\tPrevious Closing Price\tUnrealized P&L\tUnrealize P&L Pct.",
            "\tEXAMPLEIND\tINE000A00000\tFMCG\t-\t10.0000\t0.0000\t10.0000\t0.0000\t0.0000\t900.0000\t1000.0000\t1000.0000\t11.1111",
            "\tSAMPLECO\tINE000B00000\tIT\t-\t25.5000\t0.0000\t0.0000\t0.0000\t0.0000\t80.0000\t100.0000\t510.0000\t25.0000",
            "\tEXAMPLE FLEXI CAP FUND\tINF000C00000\t\tEquity\t11.0000\t0.0000\t0.0000\t0.0000\t0.0000\t90.0000\t100.0000\t110.0000\t11.1111",
        ).joinToString("\n")
    }

    /**
     * An Interactive Brokers activity statement for 2025 (Android's text): the
     * account's value (NAV) was 1,000.00 at the start and 12,345.60 at the end.
     */
    fun ibkr(): String = """
        Activity Statement
        January 1, 2025 - December 31, 2025
        Help
        Interactive Brokers (U.K.) Limited, 20 Fenchurch Street, Floor 20, London EC3M 3BY, UK.
        Account Information
        Name Jane Example
        Account U00000000
        Base Currency CHF
        Net Asset Value
        December 31, 2024 December 31, 2025
        Total Long Short Total Change
        Cash 400.00 2,345.60 0.00 2,345.60 1,945.60
        Stock 600.00 10,000.00 0.00 10,000.00 9,400.00
         Total 1,000.00 12,345.60 0.00 12,345.60 11,345.60
        Change in NAV Total
        Starting Value 1,000.00
        Mark-to-Market 845.60
        Deposits & Withdrawals 10,500.00
        Ending Value 12,345.60
        Cash Report
        Base Currency Summary
        Starting Cash 400.00 400.00 0.00
        Ending Cash 2,345.60 2,345.60 0.00
        Activity Statement - January 1, 2025 - December 31, 2025 Page: 1
    """.trimIndent()

    /** The block Android puts between an HDFC statement's pages (the page header, read out of order). */
    private fun hdfcPageBlock(page: Int) = """
        Page No .: $page Statement of account
        MR JANE EXAMPLE
        EXAMPLESTRASSE 1
        ZURICH
        JOINT HOLDERS :
        Nomination : Registered
        Statement From : 01/04/2025 To : 30/06/2025
        Account Branch : EXAMPLE NAGAR
        City : BENGALURU
        OD Limit : 0 Currency : INR
        Email : JANE@EXAMPLE.COM
        Cust ID : 000000000
        Account No : 00000000000000 Preferred Customer
        Account Type : SAVINGS - NRO (000)
        HDFC BANK LIMITED
        *Closing balance includes funds earmarked for hold and uncleared funds
        State account branch GSTN:00AAAAA0000A0ZA
        Registered Office Address: HDFC Bank House,Senapati Bapat Marg,Lower Parel,Mumbai 400013
    """.trimIndent()

    /**
     * An HDFC savings account statement (1 Apr – 30 Jun 2025), as Android
     * extracts it: rows whose description wraps before or after the amount
     * line, page-header blocks in between, no opening balance. Balances:
     * 1,00,000.00 after the first row (money in), ending at 76,420.50.
     */
    fun hdfc(): String = """
        Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Closing Balance
        02/04/25 NEFT CR-EXAMPLE EMPLOYER-SALARY APR 0000000000000001 02/04/25 50,000.00 1,00,000.00
        05/04/25 UPI-EXAMPLE CAFE-EXAMPLECAFE@YBL-YESB0YBLUPI 0000000000000002 05/04/25 450.00 99,550.00
        -000000000000-PAYMENT FOR 1234
        20/04/25 UPI-UBER INDIA SYSTEMS
        P-UBERRIDES@HDFCB
        0000000000000003 20/04/25 329.50 99,220.50
        ANK-HDFC0000000-000000000000-UBERRIDE
        ${hdfcPageBlock(1)}
        EXTRA-WRAPPED
        12/05/25 ACH D- EXAMPLE MUTUAL FUND P-000000ABCDEF 0000000000000004 12/05/25 20,000.00 79,220.50
        28/05/25 IMPS-000000000000-JOHN EXAMPLE-ICIC-XX 0000A00000000005 28/05/25 2,500.00 76,720.50
        XXXXXX0000-JOHN
        ${hdfcPageBlock(2)}
        15/06/25 UPI-GOOGLE
        PLAY-PLAYSTORE@AXISBANK-UTIB0
        0000000000000006 15/06/25 300.00 76,420.50
        000000-000000000000-MANDATEEXECUTE
        This is a computer generated statement and does
        not require signature.
        Generated On: 01-JUL-2025 10:00:00 Generated By: 000000000 Requesting Branch Code: 0000
    """.trimIndent()

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

    /**
     * A VIAC pillar 3a report (layout of the real English PDF, invented values):
     * a table of portfolios with a "Total" row on page 1, the same portfolio rows
     * again on later pages, then positions and transactions.
     * Balances: 7'629.10 + 2'070.00 = 9'699.10.
     */
    fun viac(
        asOf: String = "31.08.2026",
        mandate: String = "Pillar 3a",
        secondBalance: String = "2'070.00",
        total: String = "Total 9'056.00 7.10% 643.10 9'699.10",
    ): String {
        val footer = "Terzo Pension Foundation of WIR Bank | Auberg 1 | 4002 Basel | E-Mail info@viac.ch | Phone 0000 00 00 00 | www.viac.ch S. E. & O."
        val header = """
            Number Name Strategy Deposits 2026
            in CHF
            Return
            in % since start
            Return
            in CHF since start
            Balance
            in CHF
        """.trimIndent()
        return """
            Reporting as of $asOf
            $mandate
            Contract 1.234.567.890
            Client Test Client
            Generated on 30.09.2026
            Terzo Pension Foundation of WIR Bank
            Auberg 1
            4002 Basel
            E-Mail info@viac.ch
            Phone 0000 00 00 00
            www.viac.ch
            $footer
            $header
            1.234.567.890.01 Portfolio 1 Global 80 with bonds 7'056.00 8.12% 573.10 7'629.10
            1.234.567.890.02 Portfolio 2 Global 40 2'000.00 3.50% 70.00 $secondBalance
            $total
            Contract 1.234.567.890
            Client Test Client
            Reporting date $asOf
            Portfolio overview
            1 / 12
            $footer
            Reporting Portfolio 1.234.567.890.01 "Portfolio 1"
            as of $asOf
            Mandate $mandate
            Deposits 2026 5'000.00
            $header
            1.234.567.890.01 Portfolio 1 Global 80 with bonds 7'056.00 8.12% 573.10 7'629.10
            Asset class FX Quantity Name ISIN Initial price
            Liquidity
            3a Account CHF 120.50 1.55% Interest 0.10% 120.50
            Equity
            Switzerland CHF 12.34 Example SMI Fund CH0000000001 101.00 120.00 18.81% 19.40% 1'480.80
            Type of transaction Portfolio Amount
            in CHF
            Value date Account Balance
            in CHF
            Fee 1.234.567.890.01 «Portfolio 1» -1.25 31.08.2026 120.50
            Deposit 3a 1.234.567.890.01 «Portfolio 1» +1'000.00 15.08.2026 1'121.75
            Trade Example SMI Fund 1.234.567.890.01 «Portfolio 1» -1'000.00 16.08.2026 121.75
            Exchange rates
            USD / CHF 0.8000
            Remarks
            This extract is not to be used for tax purposes.
            Contract 1.234.567.890
            Client Test Client
            Reporting date $asOf
            Appendix
            12 / 12
        """.trimIndent()
    }

    /**
     * An invented mutual fund Consolidated Account Statement (CAMS/KFintech via MFCentral),
     * laid out like the real one: three folios, one with an SIP purchase this month.
     * Values: 56,393.66 + 30,125.00 + 10,123.40 = 96,642.06.
     */
    fun mutualFundCas(from: String = "01-Jul-2026", to: String = "31-Jul-2026", secondValuation: String = "30,125.00"): String {
        val header = """
            Consolidated Account Statement
            ( From Date : $from To Date : $to )
            MFCentralDetailCAS_v1.0_0000000000-000000000_${from}_$to _-01/08/2026 9:15:00pm Page 2 of 5
            SoA Holdings Demat Holdings
            Transaction Amount (INR) Units Price
            (INR) Date Unit Balance
        """.trimIndent()
        val nav = to.uppercase()
        return """
            Consolidated Account Statement
            ( From Date : $from To Date : $to )
            PAN: ABCDE1234F
            Test Investor
            1 Example Street
            The Consolidated Account Statement is brought to you as an investor friendly initiative by
            CAMS and KFintech, and list the transactions, balances and valuation of Mutual Funds in which you
            MFCentralDetailCAS_v1.0_0000000000-000000000_${from}_$to _-01/08/2026 9:15:00pm Page 1 of 5
            SoA Holdings Demat Holdings
            Allocation by Asset Class
            60.00%
            40.00%
            EQUITY
            DEBT FUND
            $header
            Example Mutual Fund
            FOLIO NO: 1234567890
            Example Flexi Cap Fund - Direct Plan Growth (Advisor: INZ000000000/DIRECT) ISIN: INF000A01AB1
            KYC : OK
            Opening Unit Balance: 1,124.123
            05-Jul-2026 Purchase - SIP 5,000.00 110.444 45.2718 1,234.567
            Closing Unit Balance: 1,234.567 Nav as on $nav: INR 45.6789 Valuation on $to : INR 56,393.66
            Sample Mutual Fund
            FOLIO NO: 9876543
            Sample Short Term Fund - Direct Plan Growth (Advisor: INZ000000000/DIRECT) ISIN: INF000B01CD2
            KYC : OK
            Opening Unit Balance: 250.000
            --- --- No Transaction during this statement period --- --- --- --- ---
            Closing Unit Balance: 250.000 Nav as on $nav: INR 120.50 Valuation on $to : INR $secondValuation
            $header
            FOLIO NO: 9876544
            Sample Liquid Fund - Direct Growth (Advisor: INZ000000000/DIRECT) ISIN: INF000B01EF3
            KYC : OK
            Opening Unit Balance: 1,000.000
            --- --- No Transaction during this statement period --- --- --- --- ---
            Closing Unit Balance: 1,000.000 Nav as on $nav: INR 10.1234 Valuation on $to : INR 10,123.40
            --- --- No Folios Found
            $header
            --- --- No Folios Found --- --- --- --- ---
            #IDCW - Income Distribution cum Capital Withdrawal
            *SoA - Statement of Account
        """.trimIndent()
    }
}
