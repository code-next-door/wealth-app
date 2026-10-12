package io.github.codenextdoor.wealth.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * A calculated account's value (via [valueAt]) at each month-end from the first known value up
 * to [today], newest first, skipping days with a known value. Shown, never stored: a stored one
 * would count as known and win over later changes (a rate change, the next certificate).
 */
fun monthEndValues(known: List<Pair<LocalDate, Long>>, today: LocalDate, valueAt: (LocalDate) -> Long): List<Pair<LocalDate, Long>> {
    val first = known.minOfOrNull { it.first } ?: return emptyList()
    val saved = known.map { it.first }.toSet()
    return generateSequence(YearMonth.from(first)) { it.plusMonths(1) }
        .map { it.atEndOfMonth() }
        .takeWhile { !it.isAfter(today) }
        .filter { it.isAfter(first) && it !in saved }
        .map { it to valueAt(it) }
        .toList()
        .asReversed()
}
