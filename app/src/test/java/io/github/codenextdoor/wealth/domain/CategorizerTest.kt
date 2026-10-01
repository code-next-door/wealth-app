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

    @Test
    fun incomeRulesOnlyMatchMoneyIn() {
        // "INTEREST" to Interest income (id 9), but a card's interest charge is money out.
        val categorizer = Categorizer(
            listOf(CategoryRule(1, "INTEREST", 9), CategoryRule(2, "MIGROS", 1)),
            incomeCategories = setOf(9L),
        )
        assertEquals(9L, categorizer.match("INTEREST CREDIT", moneyOut = false)?.categoryId)
        assertNull(categorizer.match("INTEREST CHARGED", moneyOut = true))
        // Spending rules match both ways: money back from Migros is a Groceries refund.
        assertEquals(1L, categorizer.match("MIGROS ZURICH", moneyOut = false)?.categoryId)
        assertEquals(1L, categorizer.match("MIGROS ZURICH", moneyOut = true)?.categoryId)
    }

    @Test
    fun aRuleWithoutACategoryNeverMatches() {
        // Old "don't import" rules (no category) from before seed version 9 or an old backup:
        // they decide nothing, so a shorter rule with a category still applies.
        val categorizer = Categorizer(listOf(CategoryRule(1, "COOP PRONTO", null), CategoryRule(2, "COOP", 7L)))
        assertEquals(2L, categorizer.match("COOP PRONTO ZUERICH")?.id)
        assertNull(Categorizer(listOf(CategoryRule(1, "MY BROKER", null))).match("MY BROKER AG"))
    }
}
