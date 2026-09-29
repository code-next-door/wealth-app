package io.github.codenextdoor.wealth.ui

import io.github.codenextdoor.wealth.ui.tour.Tour
import io.github.codenextdoor.wealth.ui.tour.TourSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TourTest {

    @Test
    fun startsAtTheFirstStopAndEndsAfterTheLast() {
        val tour = Tour(stepCount = 3)
        assertNull(tour.step.value)
        tour.start()
        assertEquals(0, tour.step.value)
        tour.next()
        tour.next()
        assertEquals(2, tour.step.value)
        tour.next()
        assertNull(tour.step.value)
        tour.next() // nothing to go on from
        assertNull(tour.step.value)
    }

    @Test
    fun canBeStoppedAnywhereAndStartedAgain() {
        val tour = Tour()
        tour.start()
        tour.next()
        tour.stop()
        assertNull(tour.step.value)
        tour.start()
        assertEquals(0, tour.step.value)
    }

    @Test
    fun sixStopsEachPointingAtSomethingDifferent() {
        assertEquals(6, TourSteps.all.size)
        assertEquals(6, TourSteps.all.map { it.target }.toSet().size)
    }
}
