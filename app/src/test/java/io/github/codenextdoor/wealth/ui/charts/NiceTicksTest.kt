package io.github.codenextdoor.wealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NiceTicksTest {

    @Test
    fun roundTicksCoverRange() {
        assertEquals(listOf(0f, 5_000f, 10_000f, 15_000f, 20_000f), niceTicks(4_200f, 16_491f))
    }

    @Test
    fun ticksBracketData() {
        val ticks = niceTicks(123_456f, 131_000f)
        assertTrue(ticks.first() <= 123_456f && ticks.last() >= 131_000f)
        assertTrue(ticks.size in 3..6)
    }

    @Test
    fun flatSeriesStillHasRange() {
        val ticks = niceTicks(1_000f, 1_000f)
        assertTrue(ticks.first() < 1_000f && ticks.last() > 1_000f)
    }
}
