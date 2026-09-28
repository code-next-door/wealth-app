package io.github.codenextdoor.wealth.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TodayTest {

    private val start = Instant.parse("2026-03-31T23:58:00Z")
    private var zone: ZoneId = ZoneOffset.UTC

    /** A clock that runs on the test's virtual time. */
    private fun TestScope.today() = Today(now = { start.plusMillis(testScheduler.currentTime) }, zone = { zone })

    @Test
    fun movesOnAtMidnight() = runTest {
        val today = today()
        backgroundScope.launch { today.keepUpToDate() }
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 31), today.date.value)

        advanceTimeBy(60_000) // 23:59
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 31), today.date.value)

        advanceTimeBy(61_000) // just past midnight
        runCurrent()
        assertEquals(LocalDate.of(2026, 4, 1), today.date.value)

        advanceTimeBy(24 * 60 * 60 * 1000L) // and the night after
        runCurrent()
        assertEquals(LocalDate.of(2026, 4, 2), today.date.value)
    }

    @Test
    fun aNewTimeZoneIsNoticedOnReturnOrWithinAnHour() = runTest {
        val midday = Instant.parse("2026-03-31T12:00:00Z")
        val today = Today(now = { midday.plusMillis(testScheduler.currentTime) }, zone = { zone })
        backgroundScope.launch { today.keepUpToDate() }
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 31), today.date.value)

        zone = ZoneId.of("Pacific/Kiritimati") // UTC+14: already 1 April there.
        advanceTimeBy(30 * 60 * 1000L)
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 31), today.date.value) // not noticed yet
        today.check() // the app comes back on screen
        assertEquals(LocalDate.of(2026, 4, 1), today.date.value)

        zone = ZoneOffset.UTC // and back, while the app stays open
        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 31), today.date.value)
    }
}
