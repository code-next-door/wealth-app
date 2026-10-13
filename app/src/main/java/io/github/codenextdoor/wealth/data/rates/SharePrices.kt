package io.github.codenextdoor.wealth.data.rates

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

/** Prices for shares known by ISIN (e.g. a demat statement's), as they were each day. */
interface IsinPriceSource {
    /** The exchange symbol for [isin] (NSE first, then BSE); null inside if it has none, a failure if unreachable. */
    suspend fun symbolFor(isin: String): Result<String?>

    /** Like [PriceSource.closes], but each close as it was that day: later splits and bonus issues undone. */
    suspend fun rawCloses(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal>
}

/**
 * Yahoo Finance's chart data: free and without an account, but unofficial,
 * so it may change; a typed price always works instead. Its closes are
 * adjusted for later splits ([closes] keeps that, like the price history).
 */
class YahooPriceSource(private val http: HttpGet) : PriceSource, IsinPriceSource {

    override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal> =
        chart(symbol, from, to, withSplits = false)?.first.orEmpty()

    override suspend fun rawCloses(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal> {
        val (closes, splits) = chart(symbol, from, to, withSplits = true) ?: return emptyMap()
        // A 2:1 split halves every earlier close; multiply it back for days before each split.
        return closes.mapValues { (day, close) ->
            splits.filter { it.first.isAfter(day) }.fold(close) { price, split -> price * split.second }.stripTrailingZeros()
        }
    }

    override suspend fun symbolFor(isin: String): Result<String?> {
        val body = http.get("https://query2.finance.yahoo.com/v1/finance/search?q=${URLEncoder.encode(isin, Charsets.UTF_8.name())}&quotesCount=5&newsCount=0")
            ?: return Result.failure(java.io.IOException("Yahoo not reachable"))
        // An answer without a usable symbol: Yahoo doesn't know it (e.g. a bond or an unlisted fund).
        return Result.success(
            runCatching {
                val symbols = Json.parseToJsonElement(body).jsonObject.getValue("quotes").jsonArray
                    .mapNotNull { it.jsonObject["symbol"]?.jsonPrimitive?.content }
                symbols.firstOrNull { it.endsWith(".NS") } ?: symbols.firstOrNull { it.endsWith(".BO") } ?: symbols.firstOrNull()
            }.getOrNull(),
        )
    }

    /** Daily closes from [from] to [to] and, if asked, the splits (day, new shares per old share); null if unreachable. */
    private suspend fun chart(symbol: String, from: LocalDate, to: LocalDate, withSplits: Boolean): Pair<Map<LocalDate, BigDecimal>, List<Pair<LocalDate, BigDecimal>>>? {
        val start = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        // Closes are adjusted for every split up to today, but only splits inside the window are listed:
        // with splits, ask up to today (closes after [to] are dropped below).
        val end = (if (withSplits) maxOf(to, LocalDate.now()) else to).plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val name = URLEncoder.encode(symbol, Charsets.UTF_8.name())
        val events = if (withSplits) "&events=split" else ""
        val body = http.get("https://query1.finance.yahoo.com/v8/finance/chart/$name?period1=$start&period2=$end&interval=1d$events")
            ?: return null
        return runCatching {
            val result = Json.parseToJsonElement(body).jsonObject.getValue("chart").jsonObject.getValue("result").jsonArray[0].jsonObject
            val zone = ZoneId.of(result.getValue("meta").jsonObject.getValue("exchangeTimezoneName").jsonPrimitive.content)
            fun dayOf(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
            val times = result.getValue("timestamp").jsonArray
            val closes = result.getValue("indicators").jsonObject.getValue("quote").jsonArray[0].jsonObject.getValue("close").jsonArray
            val prices = times.indices.mapNotNull { i ->
                // Days without a close (e.g. today, still trading) are null.
                val close = closes.getOrNull(i)?.takeIf { it != JsonNull }?.jsonPrimitive ?: return@mapNotNull null
                val price = BigDecimal(close.content).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros()
                val day = dayOf(times[i].jsonPrimitive.long)
                (day to price).takeIf { price.signum() > 0 && !day.isBefore(from) && !day.isAfter(to) }
            }.toMap()
            val splits = result["events"]?.jsonObject?.get("splits")?.jsonObject?.values.orEmpty().map { it.jsonObject }.map { split ->
                dayOf(split.getValue("date").jsonPrimitive.long) to
                    BigDecimal(split.getValue("numerator").jsonPrimitive.content).divide(BigDecimal(split.getValue("denominator").jsonPrimitive.content), 10, RoundingMode.HALF_EVEN)
            }
            prices to splits
        }.getOrNull()
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
