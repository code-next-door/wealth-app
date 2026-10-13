package io.github.codenextdoor.wealth.domain

import io.github.codenextdoor.wealth.imports.CdslStatementParser
import io.github.codenextdoor.wealth.testutil.TestStatements
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DematHistoryTest {

    private val demat = CdslStatementParser().parse(TestStatements.cdsl()).demat!!
    private fun d(day: Int, month: Int) = LocalDate.of(2026, month, day)
    private val a = "INE000A01011"
    private val b = "INE000B01022"

    @Test
    fun eachMonthEndIsQuantitiesTimesThatMonthsLastClose() {
        val closes = mapOf(
            a to mapOf(d(30, 1) to BigDecimal("80"), d(27, 2) to BigDecimal("90")),
            b to mapOf(d(30, 1) to BigDecimal("140"), d(27, 2) to BigDecimal("145")),
        )
        val result = DematHistory.monthEnds(demat, closes)
        // 31 Jan: 10 × 80 + 20 × 140; 28 Feb: 15 × 90 + 20 × 145. 31 Mar is the statement's own day.
        assertEquals(listOf(d(31, 1) to BigDecimal("3600.00"), d(28, 2) to BigDecimal("4250.00")), result.values)
        assertEquals(emptySet<String>(), result.atStatementPrice)
    }

    @Test
    fun aShareWithoutDownloadedPricesUsesTheStatementsPrice() {
        val result = DematHistory.monthEnds(demat, mapOf(a to mapOf(d(30, 1) to BigDecimal("80"), d(27, 2) to BigDecimal("90"))))
        // Sample Bank at its statement price, 150.00, every month.
        assertEquals(listOf(d(31, 1) to BigDecimal("3800.00"), d(28, 2) to BigDecimal("4350.00")), result.values)
        assertEquals(setOf(b), result.atStatementPrice)
    }

    @Test
    fun aMonthWithAShareThatHasNoPriceAtAllIsLeftOutAndCounted() {
        // Sample Bank: no download and (sold by the holdings day) no statement price.
        val noBankPrice = demat.copy(prices = demat.prices - b)
        val result = DematHistory.monthEnds(noBankPrice, mapOf(a to mapOf(d(30, 1) to BigDecimal("80"), d(27, 2) to BigDecimal("90"))))
        assertEquals(emptyList<Pair<LocalDate, BigDecimal>>(), result.values)
        assertEquals(2, result.leftOut)
    }
}
