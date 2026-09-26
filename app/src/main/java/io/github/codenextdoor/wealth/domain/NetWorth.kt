package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An account together with everything needed to value it on any date. */
data class ValuedAccount(
    val account: Account,
    val kind: AssetKind,
    /** Decimal places of the account's currency. */
    val decimals: Int,
    /** Balance history, oldest first. */
    val history: List<BalanceEntry>,
)

data class NetWorthTotals(
    val assets: BigDecimal,
    val liabilities: BigDecimal,
) {
    val netWorth: BigDecimal get() = assets - liabilities
}

/**
 * Values accounts in the base currency on any date.
 *
 * Past values use the *current* exchange rates. That way history shows how
 * the user's holdings changed, not how currencies moved (and the app only
 * knows today's rates anyway).
 */
class NetWorthCalculator(
    accounts: List<ValuedAccount>,
    private val converter: CurrencyConverter,
    val baseCurrency: String,
) {
    /** Accounts that can be converted to the base currency. */
    val included: List<ValuedAccount> = accounts.filter { converter.rate(it.account.currencyCode, baseCurrency) != null }

    /** Accounts left out of totals because an exchange rate is missing. */
    val excluded: List<ValuedAccount> = accounts - included.toSet()

    /** First day any included account has a balance, or null if none. */
    val earliestDate: LocalDate? = included.mapNotNull { it.history.firstOrNull()?.date }.minOrNull()

    /** Balance in base currency at the end of [date]; zero before the account's first entry. */
    fun valueAt(account: ValuedAccount, date: LocalDate): BigDecimal {
        val entry = account.history.lastOrNull { !it.date.isAfter(date) } ?: return BigDecimal.ZERO
        val amount = minorToDecimal(entry.balanceMinor, account.decimals)
        return converter.convert(amount, account.account.currencyCode, baseCurrency) ?: BigDecimal.ZERO
    }

    /** How much [account] adds to net worth on [date] (liabilities count negative). */
    fun contributionAt(account: ValuedAccount, date: LocalDate): BigDecimal {
        val value = valueAt(account, date)
        return if (account.kind == AssetKind.LIABILITY) value.negate() else value
    }

    fun totalsAt(date: LocalDate): NetWorthTotals {
        var assets = BigDecimal.ZERO
        var liabilities = BigDecimal.ZERO
        included.forEach {
            val value = valueAt(it, date)
            if (it.kind == AssetKind.LIABILITY) liabilities += value else assets += value
        }
        return NetWorthTotals(assets, liabilities)
    }

    fun netWorthAt(date: LocalDate): BigDecimal = totalsAt(date).netWorth

    /**
     * Net worth sampled from [from] to [to] (both included), with at most
     * about [maxPoints] points. The last point is always [to].
     */
    fun series(from: LocalDate, to: LocalDate, maxPoints: Int = 60): List<Pair<LocalDate, BigDecimal>> {
        if (from.isAfter(to)) return listOf(to to netWorthAt(to))
        val days = ChronoUnit.DAYS.between(from, to)
        val step = maxOf(1L, days / maxPoints)
        val dates = generateSequence(from) { it.plusDays(step) }.takeWhile { it.isBefore(to) }.toList() + to
        return dates.map { it to netWorthAt(it) }
    }

    /** Sum of current asset values grouped by [key]; liabilities are not included. */
    fun <K> assetBreakdown(date: LocalDate, key: (ValuedAccount) -> K): Map<K, BigDecimal> =
        included.filter { it.kind == AssetKind.ASSET }
            .groupBy(key)
            .mapValues { (_, accounts) -> accounts.fold(BigDecimal.ZERO) { sum, a -> sum + valueAt(a, date) } }
            .filterValues { it.signum() != 0 }

    /** Change in each account's contribution to net worth between two dates; unchanged accounts omitted. */
    fun changesBetween(from: LocalDate, to: LocalDate): List<Pair<ValuedAccount, BigDecimal>> =
        included.map { it to (contributionAt(it, to) - contributionAt(it, from)) }
            .filter { it.second.signum() != 0 }
            .sortedByDescending { it.second.abs() }
}
