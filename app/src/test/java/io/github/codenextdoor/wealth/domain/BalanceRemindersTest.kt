package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BalanceRemindersTest {

    private val today = LocalDate.of(2026, 10, 10)

    private fun account(id: Long, name: String, excluded: Boolean = false) =
        Account(id, name, 1, "CHF", null, 0, Instant.EPOCH, null, null, excludedFromNetWorth = excluded)

    private fun entry(accountId: Long, daysAgo: Long) = BalanceEntry(0, accountId, today.minusDays(daysAgo), 1_00)

    private fun stale(
        accounts: List<Account>,
        entries: List<BalanceEntry>,
        calculated: Set<Long> = emptySet(),
        days: Int = 35,
    ) = BalanceReminders.stale(accounts, entries, calculated, today, days).map { it.accountName to it.lastUpdated }

    @Test
    fun anAccountWhoseLatestBalanceIsOlderThanTheLimitIsListed() {
        val accounts = listOf(account(1, "Savings"), account(2, "Card"))
        val entries = listOf(entry(1, 80), entry(1, 40), entry(2, 80), entry(2, 3))
        assertEquals(listOf("Savings" to today.minusDays(40)), stale(accounts, entries))
    }

    @Test
    fun exactlyAtTheLimitIsStillFine() {
        assertEquals(emptyList<Pair<String, LocalDate>>(), stale(listOf(account(1, "Savings")), listOf(entry(1, 35))))
        assertEquals(1, stale(listOf(account(1, "Savings")), listOf(entry(1, 36))).size)
    }

    @Test
    fun theLimitCanChange() {
        val accounts = listOf(account(1, "Savings"))
        val entries = listOf(entry(1, 20))
        assertEquals(1, stale(accounts, entries, days = 14).size)
        assertEquals(0, stale(accounts, entries, days = 35).size)
    }

    @Test
    fun calculatedAccountsAndOnesLeftOutOfNetWorthAreSkipped() {
        val accounts = listOf(account(1, "Mortgage"), account(2, "Old account", excluded = true), account(3, "Savings"))
        val entries = listOf(entry(1, 400), entry(2, 400), entry(3, 400))
        assertEquals(listOf("Savings"), stale(accounts, entries, calculated = setOf(1)).map { it.first })
    }

    @Test
    fun anAccountWithoutAnyBalanceIsSkipped() {
        assertEquals(0, stale(listOf(account(1, "New")), emptyList()).size)
    }

    @Test
    fun theLongestWaitingComeFirst() {
        val accounts = listOf(account(1, "A"), account(2, "B"))
        val entries = listOf(entry(1, 50), entry(2, 90))
        assertEquals(listOf("B", "A"), stale(accounts, entries).map { it.first })
    }
}
