package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private fun rent(start: LocalDate, every: Int = 1, end: LocalDate? = null, lastAdded: LocalDate? = null) =
        RecurringExpense(1, "Rent", 2_000_00, "CHF", null, null, every, start, end, lastAdded)

    @Test
    fun monthEndsDontDrift() {
        val r = rent(LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            Recurrence.due(r, LocalDate.of(2026, 5, 15)),
        )
    }

    @Test
    fun quarterlyAndYearly() {
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1)),
            Recurrence.due(rent(LocalDate.of(2026, 1, 1), every = 3), LocalDate.of(2026, 9, 30)),
        )
        assertEquals(2, Recurrence.due(rent(LocalDate.of(2024, 6, 1), every = 12), LocalDate.of(2025, 12, 1)).size)
    }

    @Test
    fun onlyWhatWasNotAddedYetAndNothingAfterTheEnd() {
        val r = rent(LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 6, 15), lastAdded = LocalDate.of(2026, 3, 1))
        assertEquals(listOf(4, 5, 6).map { LocalDate.of(2026, it, 1) }, Recurrence.due(r, LocalDate.of(2026, 12, 1)))
        assertNull(Recurrence.next(r, LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun nextIsAfterToday() {
        val r = rent(LocalDate.of(2026, 1, 25))
        assertEquals(LocalDate.of(2026, 3, 25), Recurrence.next(r, LocalDate.of(2026, 2, 25)))
        assertEquals(LocalDate.of(2026, 1, 25), Recurrence.next(r, LocalDate.of(2025, 12, 1)))
        assertEquals(emptyList<LocalDate>(), Recurrence.due(r, LocalDate.of(2025, 12, 1))) // starts in the future
    }
}
