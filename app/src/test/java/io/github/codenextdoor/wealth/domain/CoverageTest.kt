package io.github.codenextdoor.wealth.domain

import io.github.codenextdoor.wealth.domain.Coverage.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class CoverageTest {

    private val lastMonth = YearMonth.of(2025, 6)

    private fun account(id: Long, excluded: Boolean = false) =
        Account(id, "Account $id", 1, "CHF", null, 0, Instant.EPOCH, null, null, excludedFromNetWorth = excluded)

    private fun entry(accountId: Long, date: String, minor: Long = 1_00, estimated: Boolean = false, units: BigDecimal? = null) =
        BalanceEntry(0, accountId, LocalDate.parse(date), minor, units, estimated)

    private fun spend(accountId: Long?, date: String) = Expense(0, LocalDate.parse(date), 5_00, "CHF", "SHOP", null, false, accountId, null)

    private fun of(
        accounts: List<Account>,
        entries: List<BalanceEntry> = emptyList(),
        expenses: List<Expense> = emptyList(),
        calculated: Set<Long> = emptySet(),
    ) = Coverage.of(accounts, entries, expenses, calculated, lastMonth)

    private fun ym(text: String) = YearMonth.parse(text)

    @Test
    fun monthsWithoutABalanceBetweenTheFirstOneAndLastMonthAreMissing() {
        val row = of(listOf(account(1)), listOf(entry(1, "2025-02-28"), entry(1, "2025-05-31"))).single()
        assertEquals(
            mapOf(ym("2025-02") to Mark.DONE, ym("2025-03") to Mark.MISSING, ym("2025-04") to Mark.MISSING, ym("2025-05") to Mark.DONE, ym("2025-06") to Mark.MISSING),
            row.balances!!.months,
        )
        assertEquals(listOf(ym("2025-03"), ym("2025-04"), ym("2025-06")), row.missing)
        assertNull(row.transactions)
    }

    @Test
    fun thisMonthIsNotExpectedYet() {
        val row = of(listOf(account(1)), listOf(entry(1, "2025-06-30"), entry(1, "2025-07-05"))).single()
        assertEquals(mapOf(ym("2025-06") to Mark.DONE), row.balances!!.months)
        assertTrue(row.missing.isEmpty())
    }

    @Test
    fun aMonthWithOnlyACalculatedBalanceIsMarkedSoAndNotMissing() {
        val row = of(listOf(account(1)), listOf(entry(1, "2025-05-31", estimated = true), entry(1, "2025-06-30"), entry(1, "2025-06-15", estimated = true))).single()
        assertEquals(mapOf(ym("2025-05") to Mark.CALCULATED, ym("2025-06") to Mark.DONE), row.balances!!.months)
        assertTrue(row.missing.isEmpty())
    }

    @Test
    fun transactionsHaveTheirOwnLineFromTheFirstImportedMonth() {
        val row = of(
            listOf(account(1)),
            listOf(entry(1, "2025-03-10"), entry(1, "2025-06-30")),
            listOf(spend(1, "2025-04-02"), spend(1, "2025-06-20"), spend(null, "2025-05-01")),
        ).single()
        assertEquals(mapOf(ym("2025-04") to Mark.DONE, ym("2025-05") to Mark.MISSING, ym("2025-06") to Mark.DONE), row.transactions!!.months)
        // Missing months of both lines, once each.
        assertEquals(listOf(ym("2025-04"), ym("2025-05")), row.missing)
    }

    @Test
    fun anAccountWithTransactionsButASingleBalanceDoesNotTrackBalances() {
        // e.g. a credit card paid in full: its balance was only typed once, when it was added.
        val row = of(listOf(account(1)), listOf(entry(1, "2025-01-15")), listOf(spend(1, "2025-05-03"), spend(1, "2025-06-03"))).single()
        assertNull(row.balances)
        assertTrue(row.missing.isEmpty())
    }

    @Test
    fun aClosedAccountStopsAtItsZeroBalance() {
        val row = of(listOf(account(1)), listOf(entry(1, "2025-01-31"), entry(1, "2025-03-31", minor = 0))).single()
        assertEquals(listOf(ym("2025-02")), row.missing)
        assertEquals(ym("2025-03"), row.balances!!.months.keys.max())
    }

    @Test
    fun noCashButSharesIsNotClosed() {
        val row = of(listOf(account(1)), listOf(entry(1, "2025-05-31", minor = 0, units = BigDecimal("3")))).single()
        assertEquals(listOf(ym("2025-06")), row.missing)
    }

    @Test
    fun calculatedAndLeftOutAccountsAreSkipped() {
        val rows = of(
            listOf(account(1), account(2), account(3, excluded = true)),
            listOf(entry(1, "2025-01-31"), entry(2, "2025-01-31"), entry(3, "2025-01-31")),
            calculated = setOf(2),
        )
        assertEquals(listOf(1L), rows.map { it.accountId })
    }

    @Test
    fun anAccountWhoseOnlyBalanceIsThisMonthHasNothingToShow() {
        assertTrue(of(listOf(account(1)), listOf(entry(1, "2025-07-01"))).isEmpty())
    }
}
