package io.github.codenextdoor.wealth.data.rates

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Somewhere to download daily closing share prices from. */
interface PriceSource {
    /** Closing price of [symbol] for each trading day from [from] to [to]; empty if it couldn't be reached. */
    suspend fun closes(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal>
}

/**
 * Yahoo Finance's chart data: free and without an account, but unofficial,
 * so it may change; a typed price always works instead.
 */
class YahooPriceSource(private val http: HttpGet) : PriceSource {
    override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal> {
        val start = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val end = to.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val name = URLEncoder.encode(symbol, Charsets.UTF_8.name())
        val body = http.get("https://query1.finance.yahoo.com/v8/finance/chart/$name?period1=$start&period2=$end&interval=1d")
            ?: return emptyMap()
        return runCatching {
            val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val zone = ZoneId.of(result.getJSONObject("meta").getString("exchangeTimezoneName"))
            val times = result.getJSONArray("timestamp")
            val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
            (0 until times.length()).mapNotNull { i ->
                if (closes.isNull(i)) return@mapNotNull null
                val price = BigDecimal(closes.get(i).toString()).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros()
                val day = Instant.ofEpochSecond(times.getLong(i)).atZone(zone).toLocalDate()
                (day to price).takeIf { price.signum() > 0 && !day.isBefore(from) && !day.isAfter(to) }
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

/**
 * Downloads share prices into the price history, for accounts holding shares
 * and for stock grants. Prices the user typed always win.
 */
class PriceUpdater(
    private val shares: ShareRepository,
    private val accounts: AccountRepository,
    private val source: PriceSource,
    private val today: () -> LocalDate = LocalDate::now,
) {
    data class Found(val price: BigDecimal, val date: LocalDate)

    /** Prices downloaded in this session; saved again on each use (see RateUpdater). */
    private val downloaded = ConcurrentHashMap<Pair<String, LocalDate>, Map<LocalDate, BigDecimal>>()

    /** The closing price of [symbol] on [date] (or the last trading day before), downloaded and saved. */
    suspend fun lookUp(symbol: String, date: LocalDate): Found? {
        val closes = downloaded[symbol to date]
            ?: source.closes(symbol, date.minusDays(LOOK_BACK_DAYS), date).takeIf { it.isNotEmpty() }?.also { downloaded[symbol to date] = it }
            ?: return null
        val day = closes.keys.max()
        shares.saveFetchedPrices(symbol, closes)
        return Found(closes.getValue(day), day)
    }

    /**
     * Prices for every share held or granted, from the first day they're
     * needed; later only the days not downloaded yet. Returns false if a
     * download failed (e.g. offline).
     */
    suspend fun refresh(): Boolean {
        val today = today()
        val held = accounts.accounts.first().filter { it.shareSymbol != null }
        val entries = accounts.balanceEntries.first()
        val needed = held.groupBy { it.shareSymbol!! }.mapValues { (_, accountsWithSymbol) ->
            val ids = accountsWithSymbol.map { it.id }.toSet()
            entries.filter { it.accountId in ids }.minOfOrNull { it.date } ?: today
        }.toMutableMap()
        shares.grants.first().forEach { needed.putIfAbsent(it.symbol, today.minusDays(LOOK_BACK_DAYS)) }

        var ok = true
        needed.forEach { (symbol, first) ->
            val from = maxOf(first, today.minusYears(MAX_YEARS))
            val earliest = shares.earliestFetchedDay(symbol)
            val latest = shares.latestFetchedDay(symbol)
            val ranges = if (earliest == null || latest == null) {
                listOf(from to today)
            } else {
                listOfNotNull(
                    // Older holdings added since the last download (weekends and holidays have no price).
                    (from to earliest.minusDays(1)).takeIf { from.isBefore(earliest.minusDays(LOOK_BACK_DAYS)) },
                    (latest.plusDays(1) to today).takeIf { latest.isBefore(today) },
                )
            }
            ranges.forEach { (start, end) ->
                val closes = source.closes(symbol, start, end)
                if (closes.isEmpty()) ok = false else shares.saveFetchedPrices(symbol, closes)
            }
        }
        return ok
    }

    private companion object {
        /** Long enough to find the last trading day before any date (weekends, holidays). */
        const val LOOK_BACK_DAYS = 7L
        const val MAX_YEARS = 10L
    }
}
