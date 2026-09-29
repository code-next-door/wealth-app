package io.github.codenextdoor.wealth.onboarding

import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.BalanceEntry

/** The getting-started checklist's steps, in order. */
enum class GettingStartedStep { ADD_ACCOUNT, BUILD_HISTORY, APP_LOCK, BACKUP }

/** Which steps are done, read from the real data (each ticks itself off). */
object GettingStarted {
    fun done(accounts: List<Account>, entries: List<BalanceEntry>, lockEnabled: Boolean, backupMade: Boolean): Set<GettingStartedStep> =
        buildSet {
            if (accounts.isNotEmpty()) add(GettingStartedStep.ADD_ACCOUNT)
            // History: some account has balances on at least two days.
            if (entries.groupBy { it.accountId }.values.any { list -> list.map { it.date }.distinct().size >= 2 }) {
                add(GettingStartedStep.BUILD_HISTORY)
            }
            if (lockEnabled) add(GettingStartedStep.APP_LOCK)
            if (backupMade) add(GettingStartedStep.BACKUP)
        }
}
