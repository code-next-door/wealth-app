package io.github.codenextdoor.wealth.domain

import java.time.LocalDate

/**
 * Which balances are out of date: accounts whose latest balance entry is more than
 * [days] days old. Accounts whose value the app works out itself (calculated loans,
 * pensions growing between statements, houses) and ones left out of net worth are
 * skipped, and so are accounts without any balance yet.
 */
object BalanceReminders {

    const val DEFAULT_DAYS = 35
    /** The choices in Settings › Reminders. */
    val DAY_CHOICES = listOf(14, 35, 60, 90)

    data class Stale(val accountId: Long, val accountName: String, val lastUpdated: LocalDate)

    /** Longest waiting first. [calculated]: account ids whose value is worked out. */
    fun stale(
        accounts: List<Account>,
        entries: List<BalanceEntry>,
        calculated: Set<Long>,
        today: LocalDate,
        days: Int,
    ): List<Stale> {
        val latest = entries.groupBy { it.accountId }.mapValues { (_, list) -> list.maxOf { it.date } }
        val limit = today.minusDays(days.toLong())
        return accounts
            .filter { !it.excludedFromNetWorth && it.id !in calculated }
            .mapNotNull { account -> latest[account.id]?.takeIf { it < limit }?.let { Stale(account.id, account.name, it) } }
            .sortedBy { it.lastUpdated }
    }
}
