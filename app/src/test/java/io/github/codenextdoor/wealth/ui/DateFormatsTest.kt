package io.github.codenextdoor.wealth.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DateFormatsTest {

    private val march = LocalDate.of(2026, 3, 15)

    @Test
    fun monthTitlesFollowTheRegionsOrder() {
        assertEquals("March 2026", march.format(localDateFormat("MMMMyyyy", Locale.US)))
        assertEquals("März 2026", march.format(localDateFormat("MMMMyyyy", Locale.GERMANY)))
        assertEquals("2026年3月", march.format(localDateFormat("MMMMyyyy", Locale.JAPAN)))
        assertEquals("Mar 2026", march.format(localDateFormat("MMMyyyy", Locale.US)))
    }
}
