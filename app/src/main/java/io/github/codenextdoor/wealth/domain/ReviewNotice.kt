package io.github.codenextdoor.wealth.domain

import java.time.LocalDate
import java.time.YearMonth

/** When last month's review is announced: once, in the first week of a month, with accounts. */
object ReviewNotice {
    const val FIRST_DAYS = 7

    /** The month to announce today, or null. */
    fun due(today: LocalDate, lastNotified: YearMonth?, hasAccounts: Boolean): YearMonth? {
        val month = YearMonth.from(today).minusMonths(1)
        return month.takeIf { hasAccounts && today.dayOfMonth <= FIRST_DAYS && lastNotified != month }
    }
}
