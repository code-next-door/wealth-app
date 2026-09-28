package io.github.codenextdoor.wealth.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "1 [from] = [rate] [to]" from [date] onwards (until a newer rate for the pair). */
data class RatePoint(
    val from: String,
    val to: String,
    val rate: java.math.BigDecimal,
    val date: LocalDate,
    /** Downloaded rather than typed by the user. */
    val fetched: Boolean = false,
)

/**
 * The history of exchange rates the user has entered. For any day, each
 * currency pair uses its latest rate on or before that day; before the
 * first known rate, the earliest one is used (better than leaving the
 * account out). The current rates are simply the latest ones.
 */
class RateBook(points: List<RatePoint>) {

    /** Points per currency pair (either direction), oldest first. */
    private val byPair: Collection<List<RatePoint>> =
        points.groupBy { setOf(it.from, it.to) }.values.map { list -> list.sortedBy { it.date } }

    // Rate books are read from UI and background threads alike.
    private val cache = java.util.concurrent.ConcurrentHashMap<LocalDate, CurrencyConverter>()

    /** The rate between [a] and [b] (either direction) in effect on [date], if any. */
    fun pointAt(a: String, b: String, date: LocalDate): RatePoint? =
        byPair.firstOrNull { it.first().let { p -> setOf(p.from, p.to) == setOf(a, b) } }
            ?.let { history -> history.lastOrNull { !it.date.isAfter(date) } ?: history.first() }

    /** The rates in effect on [date], one per pair. */
    fun ratesAt(date: LocalDate): List<ExchangeRate> = byPair.map { history ->
        val point = history.lastOrNull { !it.date.isAfter(date) } ?: history.first()
        ExchangeRate(point.from, point.to, point.rate, point.date.atStartOfDay().toInstant(ZoneOffset.UTC))
    }

    /** Converter using the rates in effect on [date]. */
    fun converterAt(date: LocalDate): CurrencyConverter = cache.getOrPut(date) { CurrencyConverter(ratesAt(date)) }

    /** Converter using the latest rates. */
    val current: CurrencyConverter get() = converterAt(LocalDate.MAX)

    /** The latest rate per pair, for screens that show "the" rate. */
    val currentRates: List<ExchangeRate> get() = ratesAt(LocalDate.MAX)

    companion object {
        /** Wraps plain current rates (all treated as known since forever). */
        fun of(rates: List<ExchangeRate>) = RateBook(
            rates.map { RatePoint(it.from, it.to, it.rate, it.updatedAt.coerceAtLeast(Instant.EPOCH).atZone(ZoneOffset.UTC).toLocalDate()) },
        )
    }
}
