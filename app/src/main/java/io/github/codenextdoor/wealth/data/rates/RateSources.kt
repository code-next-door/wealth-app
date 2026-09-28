package io.github.codenextdoor.wealth.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** A downloaded rate, and the day it's from (e.g. Friday's rate when asked for a Saturday). */
data class Quote(val rate: BigDecimal, val date: LocalDate)

/** Somewhere to download exchange rates from. */
interface RateSource {
    /**
     * "1 [base] = rate currency" for each of [currencies] on [date] (or the
     * last day before it with rates). Currencies it can't give are left out;
     * an empty map means it couldn't be reached.
     */
    suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote>
}

/** Fetches a URL; the body on success, null on any failure. An interface so tests never touch the network. */
fun interface HttpGet {
    suspend fun get(url: String): String?
}

/** Plain HTTPS GET with short timeouts. Sends nothing but the URL. */
object HttpsGet : HttpGet {
    override suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                // Some services refuse requests that don't say what's asking.
                connection.setRequestProperty("User-Agent", "Wealth (Android app)")
                if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/**
 * Frankfurter (frankfurter.dev): the European Central Bank's daily reference
 * rates, free and without an account. About 30 major currencies, including
 * CHF, INR and USD; no rates on weekends and holidays.
 */
class FrankfurterSource(private val http: HttpGet) : RateSource {
    override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> {
        if (currencies.isEmpty()) return emptyMap()
        val body = http.get("https://api.frankfurter.dev/v1/$date?base=$base&symbols=${currencies.sorted().joinToString(",")}")
            ?: return emptyMap()
        return parse(body) { root -> root.getValue("rates").jsonObject }.filterKeys { it in currencies }
    }
}

/**
 * fawazahmed0's currency-api, served free from the jsDelivr and Cloudflare
 * networks: 200+ currencies, for those the ECB doesn't publish.
 */
class CurrencyApiSource(private val http: HttpGet, private val today: () -> LocalDate = LocalDate::now) : RateSource {
    override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> {
        if (currencies.isEmpty()) return emptyMap()
        val version = if (date.isBefore(today())) date.toString() else "latest"
        val file = "v1/currencies/${base.lowercase()}.min.json"
        val body = http.get("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$version/$file")
            ?: http.get("https://$version.currency-api.pages.dev/$file")
            ?: return emptyMap()
        return parse(body) { root -> root.getValue(base.lowercase()).jsonObject }
            .mapKeys { it.key.uppercase() }
            .filterKeys { it in currencies }
    }
}

/** Asks [primary] first, then [secondary] for whatever [primary] didn't have. */
class FallbackRateSource(private val primary: RateSource, private val secondary: RateSource) : RateSource {
    override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> {
        val first = primary.ratesOn(base, currencies, date)
        val missing = currencies - first.keys
        return if (missing.isEmpty()) first else first + secondary.ratesOn(base, missing, date).filterKeys { it in missing }
    }
}

/** Reads `{"date": "YYYY-MM-DD", ...}` plus the object [rates] picks out; nonsense gives nothing. */
private fun parse(body: String, rates: (JsonObject) -> JsonObject): Map<String, Quote> = runCatching {
    val root = Json.parseToJsonElement(body).jsonObject
    val date = LocalDate.parse(root.getValue("date").jsonPrimitive.content)
    rates(root).mapNotNull { (code, value) ->
        // The number exactly as written (no detour through a double).
        runCatching { BigDecimal(value.jsonPrimitive.content).round(PRECISION).stripTrailingZeros() }.getOrNull()
            ?.takeIf { it.signum() > 0 }
            ?.let { code to Quote(it, date) }
    }.toMap()
}.getOrDefault(emptyMap())

/** More digits than any published rate has; drops floating-point noise from the JSON. */
private val PRECISION = MathContext(10)
