package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    private val groceries = 1L
    private val transport = 2L
    private val eatingOut = 3L
    private val utilities = 4L

    private val categorizer = Categorizer(
        listOf(
            CategoryRule(1, "COOP", groceries),
            CategoryRule(2, "UBER", transport),
            CategoryRule(3, "UBER EATS", eatingOut),
            CategoryRule(4, "MCDONALD", eatingOut),
            CategoryRule(5, "SALT", utilities),
            CategoryRule(6, "CAFE", eatingOut),
        ),
    )

    @Test
    fun normalizesCaseAccentsAndPunctuation() {
        assertEquals("CAFE BAR ZURICH", Categorizer.normalize("Café-Bar*ZÜRICH"))
        assertEquals("TWINT COOP 4521 ZUERICH", Categorizer.normalize("  twint *Coop-4521  Zuerich "))
    }

    @Test
    fun matchesKeywordAnywhereAtWordStart() {
        assertEquals(groceries, categorizer.categoryFor("TWINT *COOP-4521 ZUERICH"))
        assertEquals(eatingOut, categorizer.categoryFor("McDonald's Bahnhof"))
        assertEquals(eatingOut, categorizer.categoryFor("Café du Marché"))
    }

    @Test
    fun longestKeywordWins() {
        assertEquals(eatingOut, categorizer.categoryFor("UBER *EATS HELP.UBER.COM"))
        assertEquals(transport, categorizer.categoryFor("UBER *TRIP"))
    }

    @Test
    fun doesNotMatchInsideWords() {
        assertNull(categorizer.categoryFor("BASALT GALLERY"))
        assertEquals(utilities, categorizer.categoryFor("SALT MOBILE SA"))
    }

    @Test
    fun noMatchIsNull() {
        assertNull(categorizer.categoryFor("SOMETHING ELSE"))
        assertNull(Categorizer(emptyList()).categoryFor("COOP"))
    }

    @Test
    fun suggestsMerchantWordSkippingPaymentPrefixes() {
        assertEquals("COOP", Categorizer.suggestKeyword("TWINT *COOP-4521 ZUERICH"))
        assertEquals("MIGROS", Categorizer.suggestKeyword("Migros M Zürich HB"))
        assertEquals("SWIGGY", Categorizer.suggestKeyword("UPI/1234/SWIGGY/Bangalore"))
        assertEquals("", Categorizer.suggestKeyword("12 34"))
    }
}
