package io.github.codenextdoor.wealth.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's date for screens that value things "as of today", kept current while
 * the app stays open or sits in memory across midnight (Android often keeps an
 * app alive for days, so reading the date once at startup isn't enough).
 */
class Today(
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _date = MutableStateFlow(current())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** Reads the clock again, e.g. when the app comes back on screen. */
    fun check() {
        _date.value = current()
    }

    /**
     * Wakes at each midnight to move [date] on. Waits at most an hour, so a new
     * time zone (travel) is noticed too; timers also pause while the phone
     * sleeps, which [check] on return covers.
     */
    suspend fun keepUpToDate(): Nothing {
        while (true) {
            check()
            delay(millisUntilTomorrow().coerceIn(1, MAX_WAIT_MILLIS))
        }
    }

    private fun current(): LocalDate = now().atZone(zone()).toLocalDate()

    private fun millisUntilTomorrow(): Long {
        val zone = zone()
        val instant = now()
        val tomorrow = instant.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(instant, tomorrow).toMillis()
    }

    private companion object {
        const val MAX_WAIT_MILLIS = 60 * 60 * 1000L
    }
}
