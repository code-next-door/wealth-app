package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class PropertyValueTest {

    private fun rounded(value: BigDecimal?) = value?.setScale(0, RoundingMode.HALF_EVEN)

    private val bought = LocalDate.of(2020, 1, 1)
    private val anchors = listOf(bought to BigDecimal("100"), LocalDate.of(2022, 1, 1) to BigDecimal("121"))

    @Test
    fun nothingBeforeItWasBought() {
        assertEquals(BigDecimal.ZERO, PropertyValue.at(anchors, BigDecimal("7"), bought.minusDays(1)))
    }

    @Test
    fun anchorsAreExact() {
        assertEquals(0, BigDecimal("100").compareTo(PropertyValue.at(anchors, BigDecimal("7"), bought)))
        assertEquals(0, BigDecimal("121").compareTo(PropertyValue.at(anchors, BigDecimal("7"), LocalDate.of(2022, 1, 1))))
    }

    @Test
    fun betweenAnchorsItGrowsAtAConstantRate() {
        // 100 → 121 over two years is 10% a year: 110 after one.
        assertEquals(BigDecimal("110"), rounded(PropertyValue.at(anchors, BigDecimal("7"), LocalDate.of(2021, 1, 1))))
    }

    @Test
    fun afterTheLastAnchorItGrowsAtTheHousesRate() {
        // 121 × 1.07² ≈ 138.53 two years later.
        val value = PropertyValue.at(anchors, BigDecimal("7"), LocalDate.of(2024, 1, 1))!!.setScale(2, RoundingMode.HALF_EVEN)
        assertEquals(BigDecimal("138.53"), value)
        assertEquals(0, BigDecimal("121").compareTo(PropertyValue.at(anchors, BigDecimal.ZERO, LocalDate.of(2030, 1, 1))))
    }

    @Test
    fun aSingleAnchorGrowsFromThePurchase() {
        val one = listOf(bought to BigDecimal("1000000"))
        assertEquals(BigDecimal("1050000"), rounded(PropertyValue.at(one, BigDecimal("5"), LocalDate.of(2021, 1, 1))))
    }
}
