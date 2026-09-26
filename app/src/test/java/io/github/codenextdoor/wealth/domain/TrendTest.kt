package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TrendTest {

    private val start: LocalDate = LocalDate.of(2026, 1, 1)

    @Test
    fun fitsExactLine() {
        // +10 per day.
        val points = (0..60 step 7).map { start.plusDays(it.toLong()) to BigDecimal(1000 + 10 * it) }
        val trend = Trend.fit(points)!!
        assertEquals(0, BigDecimal("10").compareTo(trend.slopePerDay))
        assertEquals(0, BigDecimal("304.375").compareTo(trend.perMonth))
        val projected = trend.project(BigDecimal("2000"), start, start.plusDays(30))
        assertEquals(0, BigDecimal("2300").compareTo(projected))
    }

    @Test
    fun negativeTrend() {
        val points = listOf(start to BigDecimal("500"), start.plusDays(50) to BigDecimal("0"))
        assertEquals(0, BigDecimal("-10").compareTo(Trend.fit(points)!!.slopePerDay))
    }

    @Test
    fun needsEnoughHistory() {
        assertNull(Trend.fit(emptyList()))
        assertNull(Trend.fit(listOf(start to BigDecimal.ONE)))
        assertNull(Trend.fit(listOf(start to BigDecimal.ONE, start.plusDays(10) to BigDecimal.TEN)))
    }
}
