package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** "One [symbol] share cost [price]" on [date] (in the currency of the accounts holding it). */
data class PricePoint(
    val symbol: String,
    val date: LocalDate,
    val price: BigDecimal,
    /** Downloaded rather than typed by the user. */
    val fetched: Boolean = false,
)

/**
 * Share prices by day, like [RateBook] for exchange rates: for any day, the
 * latest price on or before it; before the first known price, the earliest.
 */
class PriceBook(points: List<PricePoint>) {
    private val bySymbol: Map<String, List<PricePoint>> = points.groupBy { it.symbol }.mapValues { (_, list) -> list.sortedBy { it.date } }

    fun pointAt(symbol: String, date: LocalDate): PricePoint? =
        bySymbol[symbol]?.let { history -> history.lastOrNull { !it.date.isAfter(date) } ?: history.first() }

    fun priceAt(symbol: String, date: LocalDate): BigDecimal? = pointAt(symbol, date)?.price

    companion object {
        val EMPTY = PriceBook(emptyList())
    }
}

/** A number of shares for display: "2.083", "1,250" (up to three decimals, as statements show). */
fun formatUnits(units: BigDecimal, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    java.text.NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 3
        roundingMode = RoundingMode.HALF_EVEN
    }.format(units)

/**
 * What an account holding [units] shares plus [cash] is worth at [price].
 * Null when there are shares but no price to value them.
 */
fun holdingValue(cash: BigDecimal, units: BigDecimal?, price: BigDecimal?): BigDecimal? = when {
    units == null || units.signum() == 0 -> cash
    price == null -> null
    else -> cash + units * price
}

/**
 * A grant of company shares (e.g. GSUs/RSUs) that vest over time: [totalUnits]
 * in equal parts every [intervalMonths] from [vestStart] for [vestMonths].
 * With a cliff, nothing vests for the first [cliffMonths]; then those months
 * vest together.
 */
data class Grant(
    val id: Long,
    val name: String,
    val symbol: String,
    /** Currency the share price is in, e.g. USD. */
    val currencyCode: String,
    val grantDate: LocalDate,
    val totalUnits: BigDecimal,
    val vestStart: LocalDate,
    val vestMonths: Int,
    val intervalMonths: Int,
    val cliffMonths: Int,
    val note: String?,
)

data class Vest(val date: LocalDate, val units: BigDecimal)

object Vesting {
    /** Shares are tracked to three decimals, as stock plan statements do. */
    private const val UNIT_DECIMALS = 3

    /** Every vest of [grant], oldest first; the units add up to exactly the total. */
    fun schedule(grant: Grant): List<Vest> {
        if (grant.intervalMonths <= 0 || grant.vestMonths < grant.intervalMonths) return emptyList()
        val periods = grant.vestMonths / grant.intervalMonths
        val perPeriod = grant.totalUnits.divide(BigDecimal(periods), UNIT_DECIMALS, RoundingMode.DOWN)
        val vests = mutableListOf<Vest>()
        var waiting = 0
        for (k in 1..periods) {
            waiting++
            val months = k * grant.intervalMonths
            if (months < grant.cliffMonths && k < periods) continue
            vests += Vest(grant.vestStart.plusMonths(months.toLong()), perPeriod * BigDecimal(waiting))
            waiting = 0
        }
        // Rounding leftovers go to the last vest.
        val given = vests.fold(BigDecimal.ZERO) { sum, v -> sum + v.units }
        vests[vests.lastIndex] = vests.last().copy(units = vests.last().units + (grant.totalUnits - given))
        return vests
    }

    /** Units not yet vested at the end of [date]. */
    fun unvested(grant: Grant, date: LocalDate): BigDecimal =
        schedule(grant).filter { it.date.isAfter(date) }.fold(BigDecimal.ZERO) { sum, v -> sum + v.units }

    /** The first vest after [date], if any. */
    fun next(grant: Grant, date: LocalDate): Vest? = schedule(grant).firstOrNull { it.date.isAfter(date) }
}
