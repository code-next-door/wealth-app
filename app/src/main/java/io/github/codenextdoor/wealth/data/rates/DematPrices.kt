package io.github.codenextdoor.wealth.data.rates

import io.github.codenextdoor.wealth.domain.DematHistory
import io.github.codenextdoor.wealth.imports.ParsedStatement
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Month-ends for a demat statement: its exact quantities × each share's close that
 * month, downloaded by ISIN. Sends only ISINs and dates. A share whose download
 * disagrees with the statement's own price on its holdings day (more than
 * [TOLERANCE]) isn't trusted: it's valued at the statement's price instead.
 */
class DematPrices(private val source: IsinPriceSource) {

    /**
     * [statement] with its month-ends as calculated balances; [atStatementPrice] shares were
     * valued at the statement's price; [leftOut] month-ends had a share without any price; [offline]: Yahoo couldn't be reached, so no month-ends.
     */
    data class Result(val statement: ParsedStatement, val atStatementPrice: Int = 0, val offline: Boolean = false, val leftOut: Int = 0)

    suspend fun withMonthEnds(statement: ParsedStatement): Result {
        val demat = statement.demat ?: return Result(statement)
        val closes = mutableMapOf<String, Map<LocalDate, BigDecimal>>()
        var reached = false
        demat.quantities.keys.forEach { isin ->
            val lookup = source.symbolFor(isin)
            if (lookup.isSuccess) reached = true
            // Not known to Yahoo: valued at the statement's price (see DematHistory).
            val symbol = lookup.getOrNull() ?: return@forEach
            val raw = source.rawCloses(symbol, demat.from.minusDays(LOOK_BACK_DAYS), demat.asOn)
            val own = demat.prices[isin]
            val then = raw.filterKeys { !it.isAfter(demat.asOn) }.maxByOrNull { it.key }?.value
            val trusted = own == null || (then != null && (then - own).abs() <= own * TOLERANCE)
            if (raw.isNotEmpty() && trusted) closes[isin] = raw
        }
        if (!reached) return Result(statement, offline = true)
        val result = DematHistory.monthEnds(demat, closes)
        return Result(
            statement.copy(balances = result.values, balancesEstimated = result.values.isNotEmpty()),
            atStatementPrice = result.atStatementPrice.size,
            leftOut = result.leftOut,
        )
    }

    private companion object {
        val TOLERANCE = BigDecimal("0.03")
        /** So the first month-end has a close even if the period starts on a holiday. */
        const val LOOK_BACK_DAYS = 10L
    }
}

/** No downloads (tests, previews): demat statements give only their own total. */
object NoIsinPrices : IsinPriceSource {
    override suspend fun symbolFor(isin: String): Result<String?> = Result.failure(UnsupportedOperationException("No downloads"))
    override suspend fun rawCloses(symbol: String, from: LocalDate, to: LocalDate) = emptyMap<LocalDate, BigDecimal>()
}
