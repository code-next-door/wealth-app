package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FigureMaskTest {

    private val nbsp = ' '

    @Test
    fun moneyInEveryStyleTheAppShows() {
        assertEquals("CHF$nbsp••••", maskFigures("CHF${nbsp}1’234.50"))
        assertEquals("₹••••", maskFigures("₹12,34,567.00")) // lakh grouping
        assertEquals("$••••", maskFigures("$1,234.50"))
        assertEquals("US$••••", maskFigures("US$1,234.50"))
        assertEquals("-$••••", maskFigures("-$1,234.50"))
        assertEquals("CHF-••••", maskFigures("CHF-12’345.50"))
        assertEquals("••••$nbsp€", maskFigures("1.234,50$nbsp€"))
        assertEquals("¥••••", maskFigures("¥1,234"))
    }

    @Test
    fun shortFormsHideTheirSizeToo() {
        assertEquals("CHF$nbsp••••", maskFigures("CHF${nbsp}1.25M"))
        assertEquals("CHF$nbsp••••", maskFigures("CHF${nbsp}45K"))
        assertEquals("₹••••", maskFigures("₹12.5L"))
        assertEquals("₹••••", maskFigures("₹1.2Cr"))
        assertEquals("••••${nbsp}CHF", maskFigures("1.2${nbsp}Mio.${nbsp}CHF"))
    }

    @Test
    fun percentagesAndShares() {
        assertEquals("+••••%", maskFigures("+3.2%"))
        assertEquals("−••••%", maskFigures("−12.0%"))
        assertEquals("••••$nbsp%", maskFigures("3,3$nbsp%"))
        assertEquals("•••• GOOG × $••••", maskFigures("12.5 GOOG × $150.00"))
    }

    @Test
    fun theSameMaskWhateverTheSize() {
        assertEquals(maskFigures("CHF 5.00"), maskFigures("CHF 5’000’000.00"))
    }

    @Test
    fun wordsStayAsTheyAre() {
        assertEquals("Groceries", maskFigures("Groceries"))
        assertEquals("", maskFigures(""))
        assertEquals("•••• KFC", maskFigures("1,234 KFC")) // "K" of a word isn't a short form
    }
}
