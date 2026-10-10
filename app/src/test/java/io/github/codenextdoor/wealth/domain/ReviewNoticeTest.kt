package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReviewNoticeTest {

    private val september = YearMonth.of(2026, 9)

    @Test
    fun lastMonthIsAnnouncedOnceInTheFirstWeek() {
        assertEquals(september, ReviewNotice.due(LocalDate.of(2026, 10, 1), lastNotified = null, hasAccounts = true))
        assertEquals(september, ReviewNotice.due(LocalDate.of(2026, 10, 7), lastNotified = YearMonth.of(2026, 8), hasAccounts = true))
        assertNull(ReviewNotice.due(LocalDate.of(2026, 10, 2), lastNotified = september, hasAccounts = true))
    }

    @Test
    fun notAfterTheFirstWeekNorWithoutAccounts() {
        assertNull(ReviewNotice.due(LocalDate.of(2026, 10, 8), lastNotified = null, hasAccounts = true))
        assertNull(ReviewNotice.due(LocalDate.of(2026, 10, 1), lastNotified = null, hasAccounts = false))
    }
}
