package io.github.codenextdoor.wealth.domain

import java.time.LocalDate

/**
 * An expense that repeats every [intervalMonths] from [startDate] (rent,
 * insurance, subscriptions), optionally until [endDate]. The app adds each
 * one as an ordinary expense on its day; [lastAdded] is the latest day added.
 */
data class RecurringExpense(
    val id: Long,
    val description: String,
    /** Minor units, positive. */
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: Long?,
    val accountId: Long?,
    val intervalMonths: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lastAdded: LocalDate?,
)

object Recurrence {
    /**
     * Every day it falls on, counted from the start each time so month ends
     * don't drift (Jan 31 → Feb 28 → Mar 31), up to [until] and the end date.
     */
    private fun dates(recurring: RecurringExpense, until: LocalDate): Sequence<LocalDate> {
        val last = listOfNotNull(until, recurring.endDate).min()
        return generateSequence(0L) { it + 1 }
            .map { k -> recurring.startDate.plusMonths(k * recurring.intervalMonths.coerceAtLeast(1)) }
            .takeWhile { !it.isAfter(last) }
    }

    /** Days up to [today] that haven't been added as expenses yet, oldest first. */
    fun due(recurring: RecurringExpense, today: LocalDate): List<LocalDate> =
        dates(recurring, today).filter { recurring.lastAdded == null || it.isAfter(recurring.lastAdded) }.toList()

    /** The next day after [today], if it hasn't ended. */
    fun next(recurring: RecurringExpense, today: LocalDate): LocalDate? =
        dates(recurring, today.plusYears(2)).firstOrNull { it.isAfter(today) }
}
