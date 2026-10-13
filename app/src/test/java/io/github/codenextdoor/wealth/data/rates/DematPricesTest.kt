package io.github.codenextdoor.wealth.data.rates

import io.github.codenextdoor.wealth.imports.CdslStatementParser
import io.github.codenextdoor.wealth.imports.StatementHistory
import io.github.codenextdoor.wealth.testutil.TestStatements
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DematPricesTest {

    private val statement = CdslStatementParser().parse(TestStatements.cdsl())
    private fun d(day: Int, month: Int) = LocalDate.of(2026, month, day)

    /** Example Industries (EXAMPLE.NS) closes 80 / 90 / [marchClose]; Sample Bank has no symbol unless [bank]. */
    private fun source(marchClose: String = "100", bank: Boolean = true, online: Boolean = true) = object : IsinPriceSource {
        override suspend fun symbolFor(isin: String): Result<String?> = when {
            !online -> Result.failure(java.io.IOException("offline"))
            isin == "INE000A01011" -> Result.success("EXAMPLE.NS")
            isin == "INE000B01022" && bank -> Result.success("SAMPLE.NS")
            else -> Result.success(null)
        }
        override suspend fun rawCloses(symbol: String, from: LocalDate, to: LocalDate) = when (symbol) {
            "EXAMPLE.NS" -> mapOf(d(30, 1) to BigDecimal("80"), d(27, 2) to BigDecimal("90"), d(31, 3) to BigDecimal(marchClose))
            else -> mapOf(d(30, 1) to BigDecimal("140"), d(27, 2) to BigDecimal("145"), d(31, 3) to BigDecimal("150"))
        }
    }

    @Test
    fun monthEndsAreAddedAsCalculatedAndTheStatementsTotalStaysReal() = runBlocking {
        val result = DematPrices(source()).withMonthEnds(statement)
        val points = StatementHistory.points(result.statement)
        assertEquals(listOf(d(31, 1), d(28, 2), d(31, 3)), points.map { it.date })
        assertEquals(listOf("3600.00", "4250.00", "3300.00").map(::BigDecimal), points.map { it.amount })
        assertEquals(listOf(true, true, false), points.map { it.estimated })
        assertEquals(0, result.atStatementPrice)
        assertFalse(result.offline)
    }

    @Test
    fun aDownloadedPriceFarFromTheStatementsIsNotTrusted() = runBlocking {
        // Yahoo says 60 on the holdings day, the statement 100: wrong share or not adjusted; use the statement's price.
        val result = DematPrices(source(marchClose = "60")).withMonthEnds(statement)
        assertEquals(listOf("3800.00", "4400.00", "3300.00").map(::BigDecimal), StatementHistory.points(result.statement).map { it.amount })
        assertEquals(1, result.atStatementPrice)
    }

    @Test
    fun aShareYahooDoesntKnowUsesTheStatementsPriceAndIsNotOffline() = runBlocking {
        val result = DematPrices(source(bank = false)).withMonthEnds(statement)
        assertFalse(result.offline)
        assertEquals(1, result.atStatementPrice)
        assertEquals(listOf(d(31, 1), d(28, 2), d(31, 3)), StatementHistory.points(result.statement).map { it.date })
    }

    @Test
    fun offlineOnlyTheStatementsOwnTotal() = runBlocking {
        val result = DematPrices(source(online = false)).withMonthEnds(statement)
        assertEquals(listOf(d(31, 3)), StatementHistory.points(result.statement).map { it.date })
        assertTrue(result.offline)
    }
}
