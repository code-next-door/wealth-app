package io.github.codenextdoor.wealth.domain

import java.time.YearMonth

/**
 * Which months each account has a statement for: a balance, and (for accounts with
 * imported transactions) transactions. A month is expected from the account's first
 * one up to [lastMonth] (this month isn't over yet), or up to a closing zero balance.
 * Skipped like balance reminders: accounts the app works out itself and ones left out
 * of net worth. An account with transactions and a single balance (typed once when it
 * was added, e.g. a card paid in full) doesn't track balances, so has no balance line.
 */
object Coverage {

    enum class Mark { DONE, CALCULATED, MISSING }

    /** Every expected month, oldest first. */
    data class Line(val months: Map<YearMonth, Mark>)

    data class AccountCoverage(val accountId: Long, val balances: Line?, val transactions: Line?) {
        /** Months either line is missing, oldest first. */
        val missing: List<YearMonth>
            get() = (listOfNotNull(balances, transactions).flatMap { line -> line.months.filterValues { it == Mark.MISSING }.keys }).distinct().sorted()
    }

    /** [calculated]: account ids whose value is worked out. Accounts with nothing expected are left out. */
    fun of(
        accounts: List<Account>,
        entries: List<BalanceEntry>,
        expenses: List<Expense>,
        calculated: Set<Long>,
        lastMonth: YearMonth,
    ): List<AccountCoverage> {
        val entriesByAccount = entries.groupBy { it.accountId }
        val spendMonths = expenses.filter { it.accountId != null }.groupBy({ it.accountId!! }, { YearMonth.from(it.date) }).mapValues { it.value.toSet() }
        return accounts.filter { !it.excludedFromNetWorth && it.id !in calculated }.mapNotNull { account ->
            val own = entriesByAccount[account.id].orEmpty()
            val txMonths = spendMonths[account.id].orEmpty()
            val balanceMonths = own.groupBy { YearMonth.from(it.date) }
            val latest = own.maxByOrNull { it.date }
            val closed = latest != null && latest.balanceMinor == 0L && (latest.units == null || latest.units.signum() == 0)
            val end = if (closed) minOf(lastMonth, listOfNotNull(YearMonth.from(latest!!.date), txMonths.maxOrNull()).max()) else lastMonth
            val balances = if (balanceMonths.isEmpty() || (balanceMonths.size == 1 && txMonths.isNotEmpty())) null else {
                line(balanceMonths.keys.min(), end) { month ->
                    val inMonth = balanceMonths[month]
                    when {
                        inMonth == null -> Mark.MISSING
                        inMonth.any { !it.estimated } -> Mark.DONE
                        else -> Mark.CALCULATED
                    }
                }
            }
            val transactions = if (txMonths.isEmpty()) null else line(txMonths.min(), end) { if (it in txMonths) Mark.DONE else Mark.MISSING }
            AccountCoverage(account.id, balances, transactions).takeIf { it.balances != null || it.transactions != null }
        }
    }

    /** Null when nothing is expected yet (the first month is this month or later). */
    private fun line(from: YearMonth, to: YearMonth, mark: (YearMonth) -> Mark): Line? {
        if (from > to) return null
        return Line(generateSequence(from) { it.plusMonths(1) }.takeWhile { it <= to }.associateWith(mark))
    }
}
