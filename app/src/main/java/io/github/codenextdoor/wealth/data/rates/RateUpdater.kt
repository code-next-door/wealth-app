package io.github.codenextdoor.wealth.data.rates

import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads exchange rates into the dated rate history. Rates the user typed
 * always win: a downloaded rate never replaces one (see
 * [CurrencyRepository.saveFetchedRates]).
 */
class RateUpdater(
    private val currencies: CurrencyRepository,
    private val accounts: AccountRepository,
    private val source: RateSource,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** A downloaded rate as forms show it: units of the base currency per 1 unit of the other. */
    data class Found(val rate: BigDecimal, val date: LocalDate)

    data class RefreshResult(val reachedSource: Boolean, val saved: Int)

    /**
     * Rates already downloaded in this session, so reopening a form doesn't download
     * again. They're still saved each time: the saved copy may be gone (e.g. a backup
     * was restored since).
     */
    private val downloaded = ConcurrentHashMap<Triple<String, String, LocalDate>, Quote>()

    /**
     * The rate between [currency] and the base currency for [date], downloaded
     * and saved (unless the user typed one for that day). Null for the base
     * currency itself, or when no rate could be downloaded.
     */
    suspend fun lookUp(currency: String, date: LocalDate): Found? {
        val base = currencies.baseCurrency.first()
        if (currency == base) return null
        val key = Triple(base, currency, date)
        val quote = downloaded[key] ?: source.ratesOn(base, setOf(currency), date)[currency]?.also { downloaded[key] = it } ?: return null
        currencies.saveFetchedRates(base, quote.date, mapOf(currency to quote.rate))
        return Found(BigDecimal.ONE.divide(quote.rate, CurrencyConverter.MATH), quote.date)
    }

    /**
     * Today's rates for every currency, then rates for past balance dates that
     * have none from the week before (so the history chart uses each day's
     * real rate). Run when the app opens and from the Currencies screen.
     */
    suspend fun refresh(): RefreshResult {
        val base = currencies.baseCurrency.first()
        val others = currencies.currencies.first().map { it.code }.filter { it != base }.toSet()
        if (others.isEmpty()) return RefreshResult(reachedSource = true, saved = 0)

        var saved = 0
        val todays = source.ratesOn(base, others, today())
        saved += save(base, todays)

        val book = currencies.rateBook.first()
        val currencyOf = accounts.accounts.first().associate { it.id to it.currencyCode }
        val missing = accounts.balanceEntries.first()
            .mapNotNull { entry -> currencyOf[entry.accountId]?.takeIf { it in others }?.let { it to entry.date } }
            .filter { (currency, date) ->
                val point = book.pointAt(base, currency, date)
                point == null || point.date.isAfter(date) || point.date.isBefore(date.minusDays(NEARBY_DAYS))
            }
            .groupBy({ it.second }, { it.first })
        missing.keys.sortedDescending().take(MAX_PAST_DAYS).forEach { date ->
            saved += save(base, source.ratesOn(base, missing.getValue(date).toSet(), date))
        }
        return RefreshResult(reachedSource = todays.isNotEmpty(), saved = saved)
    }

    private suspend fun save(base: String, quotes: Map<String, Quote>): Int =
        quotes.entries.groupBy { it.value.date }.entries.sumOf { (date, entries) ->
            currencies.saveFetchedRates(base, date, entries.associate { it.key to it.value.rate })
        }

    private companion object {
        /** A rate from up to a week before a balance is close enough (weekends, holidays). */
        const val NEARBY_DAYS = 6L

        /** Keeps one refresh from sending hundreds of requests; the rest follow next time. */
        const val MAX_PAST_DAYS = 60
    }
}
