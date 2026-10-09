package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionSearchTest {

    private fun expense(
        description: String,
        amountMinor: Long = 12_50,
        currency: String = "CHF",
        note: String? = null,
        parts: List<ExpensePart> = emptyList(),
    ) = Expense(1, LocalDate.of(2026, 3, 1), amountMinor, currency, description, null, false, null, note, parts = parts)

    private fun matches(query: String, expense: Expense, category: String? = null, account: String? = null, partCategories: List<String> = emptyList()) =
        TransactionSearch.parse(query)!!.matches(
            TransactionSearch.Candidate(expense, decimals = if (expense.currencyCode == "JPY") 0 else 2, categoryName = category, accountName = account, partCategoryNames = partCategories),
        )

    @Test
    fun anEmptyQuerySearchesNothing() {
        assertNull(TransactionSearch.parse(""))
        assertNull(TransactionSearch.parse("   "))
    }

    @Test
    fun textMatchesAnywhereIgnoringCaseAndAccents() {
        val coop = expense("TWINT *COOP-4521 ZÜRICH")
        assertTrue(matches("coop", coop))
        assertTrue(matches("zurich", coop))
        assertTrue(matches("Zürich", coop))
        assertFalse(matches("migros", coop))
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        val coop = expense("COOP ZURICH")
        assertTrue(matches("coop zurich", coop))
        assertTrue(matches("coop groceries", coop, category = "Groceries"))
        assertFalse(matches("coop bern", coop))
    }

    @Test
    fun noteCategoryAccountAndPartsCount() {
        val bill = expense(
            "AMAZON",
            note = "birthday present",
            parts = listOf(ExpensePart(amountMinor = 5_00, categoryId = 9, note = "batteries")),
        )
        assertTrue(matches("birthday", bill))
        assertTrue(matches("batteries", bill))
        assertTrue(matches("shopping", bill, category = "Shopping"))
        assertTrue(matches("ubs card", bill, account = "UBS card"))
        assertTrue(matches("household", bill, partCategories = listOf("Household")))
    }

    @Test
    fun aNumberWithDecimalsMatchesThatAmountExactly() {
        val coffee = expense("CAFE", amountMinor = 45_30)
        assertTrue(matches("45.30", coffee))
        assertTrue(matches("45,30", coffee))
        assertTrue(matches("45.3", coffee))
        assertFalse(matches("45.31", coffee))
    }

    @Test
    fun aWholeNumberMatchesThatAmountWithAnyCents() {
        assertTrue(matches("45", expense("CAFE", amountMinor = 45_00)))
        assertTrue(matches("45", expense("CAFE", amountMinor = 45_99)))
        assertFalse(matches("45", expense("CAFE", amountMinor = 46_00)))
        assertFalse(matches("45", expense("CAFE", amountMinor = 145_00)))
    }

    @Test
    fun amountsMatchEitherWayAndWithThousandsSeparators() {
        assertTrue(matches("1234.50", expense("REFUND", amountMinor = -1_234_50)))
        assertTrue(matches("1'234.50", expense("RENT", amountMinor = 1_234_50)))
        assertTrue(matches("1,234.50", expense("RENT", amountMinor = 1_234_50)))
    }

    @Test
    fun aSplitPartsAmountMatchesToo() {
        val bill = expense("AMAZON", amountMinor = 30_00, parts = listOf(ExpensePart(amountMinor = 7_25, categoryId = null, note = null)))
        assertTrue(matches("7.25", bill))
    }

    @Test
    fun aNumberAlsoMatchesText() {
        // "24" is in the name, not the amount.
        assertTrue(matches("24", expense("SHOP 24 BERN", amountMinor = 3_00)))
    }

    @Test
    fun currenciesWithoutCentsMatch() {
        assertTrue(matches("1500", expense("TOKYO", amountMinor = 1500, currency = "JPY")))
    }

    @Test
    fun totalsArePerCurrencyAndDirection() {
        val totals = TransactionSearch.totals(
            listOf(
                expense("A", amountMinor = 10_00),
                expense("B", amountMinor = 5_50),
                expense("C", amountMinor = -2_00),
                expense("D", amountMinor = 1_000_00, currency = "INR"),
            ),
        )
        assertEquals(
            listOf(
                TransactionSearch.Total("CHF", outMinor = 15_50, inMinor = 2_00),
                TransactionSearch.Total("INR", outMinor = 1_000_00, inMinor = 0),
            ),
            totals,
        )
    }
}
