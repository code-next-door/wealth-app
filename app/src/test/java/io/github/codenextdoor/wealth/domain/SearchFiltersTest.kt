package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Search's filters (category, account), the shop word, and the 12-month chart. */
class SearchFiltersTest {

    private val groceries = 10L
    private val household = 11L
    private val salary = 20L

    private fun expense(
        id: Long,
        description: String,
        amountMinor: Long,
        category: Long? = groceries,
        account: Long? = 1,
        date: LocalDate = LocalDate.of(2026, 3, 5),
        currency: String = "CHF",
        parts: List<ExpensePart> = emptyList(),
    ) = TransactionSearch.Candidate(
        Expense(id, date, amountMinor, currency, description, category, false, account, null, parts = parts),
        decimals = 2,
        categoryName = null,
        accountName = null,
    )

    private fun find(candidates: List<TransactionSearch.Candidate>, text: String = "", category: TransactionSearch.CategoryFilter? = null, account: Long? = null) =
        TransactionSearch.find(candidates, TransactionSearch.parse(text), category, account)

    @Test
    fun nothingTypedAndNoFilterFindsNothing() {
        assertEquals(null, find(listOf(expense(1, "COOP", 45_30))))
    }

    @Test
    fun aCategoryAloneFindsItsTransactionsAndCountsOnlyItsPartOfSplits() {
        val split = expense(3, "MIGROS", 100_00, parts = listOf(ExpensePart(amountMinor = 30_00, categoryId = household)))
        val all = listOf(expense(1, "COOP", 45_30), expense(2, "SBB", 20_00, category = null), split)

        val groceriesHits = find(all, category = TransactionSearch.CategoryFilter.Is(groceries))!!
        assertEquals(listOf(1L, 3L), groceriesHits.map { it.expense.id })
        assertEquals(listOf(45_30L, 70_00L), groceriesHits.map { it.countedMinor }) // the rest of the split

        assertEquals(listOf(30_00L), find(all, category = TransactionSearch.CategoryFilter.Is(household))!!.map { it.countedMinor })
        assertEquals(listOf(2L), find(all, category = TransactionSearch.CategoryFilter.None)!!.map { it.expense.id })
    }

    @Test
    fun textCategoryAndAccountCombine() {
        val all = listOf(expense(1, "COOP", 45_30), expense(2, "COOP", 12_00, account = 2), expense(3, "MIGROS", 9_00))
        assertEquals(listOf(1L), find(all, "coop", TransactionSearch.CategoryFilter.Is(groceries), account = 1)!!.map { it.expense.id })
        assertEquals(listOf(2L), find(all, account = 2)!!.map { it.expense.id })
    }

    @Test
    fun theShopIsThePatternThatMatchedElseTheFirstWord() {
        val categorizer = Categorizer(listOf(CategoryRule(1, "COOP", groceries), CategoryRule(2, "UBER EATS", groceries)))
        assertEquals("COOP", TransactionSearch.shopOf("TWINT *COOP-4521 ZÜRICH 12.03", categorizer, moneyOut = true))
        assertEquals("UBER EATS", TransactionSearch.shopOf("UBER EATS HELP.UBER.COM", categorizer, moneyOut = true))
        // No pattern: the first word that isn't a number or a payment method.
        assertEquals("MIGROLINO", TransactionSearch.shopOf("TWINT 4521 MIGROLINO BERN", categorizer, moneyOut = true))
        assertEquals(null, TransactionSearch.shopOf("12.03 4521", categorizer, moneyOut = true))
    }

    @Test
    fun theChartSumsMoneyOutPerMonthInTheBaseCurrency() {
        val months = TransactionSearch.lastMonths(YearMonth.of(2026, 3), 3)
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), months)
        val hits = find(
            listOf(
                expense(1, "COOP", 40_00, date = LocalDate.of(2026, 3, 5)),
                expense(2, "COOP", 10_00, date = LocalDate.of(2026, 3, 20), currency = "EUR"),
                expense(3, "COOP REFUND", -5_00, date = LocalDate.of(2026, 3, 21)), // money in: not a bar
                expense(4, "COOP", 7_00, date = LocalDate.of(2026, 1, 2)),
                expense(5, "COOP", 99_00, date = LocalDate.of(2025, 12, 31)), // before the chart
                expense(6, "COOP", 1_00, date = LocalDate.of(2026, 2, 1), currency = "XYZ"), // no rate: left out
            ),
            "coop",
        )!!
        // 1 EUR = 0.95 CHF.
        val toBase: (BigDecimal, String, LocalDate) -> BigDecimal? = { amount, code, _ ->
            when (code) {
                "CHF" -> amount
                "EUR" -> amount * BigDecimal("0.95")
                else -> null
            }
        }
        val bars = TransactionSearch.monthly(hits, months, moneyIn = false, decimalsOf = { 2 }, toBase = toBase)
        assertEquals(listOf("7.00", "0", "49.50").map(::BigDecimal).map { it.stripTrailingZeros() }, bars.map { it.stripTrailingZeros() })
    }

    @Test
    fun anIncomeCategoryChartsMoneyIn() {
        val months = TransactionSearch.lastMonths(YearMonth.of(2026, 3), 1)
        val hits = find(listOf(expense(1, "SALARY", -8_000_00, category = salary)), category = TransactionSearch.CategoryFilter.Is(salary))!!
        val bars = TransactionSearch.monthly(hits, months, moneyIn = true, decimalsOf = { 2 }, toBase = { amount, _, _ -> amount })
        assertTrue(bars.single().compareTo(BigDecimal("8000")) == 0)
    }

    @Test
    fun totalsFollowTheCountedPart() {
        val split = expense(3, "MIGROS", 100_00, parts = listOf(ExpensePart(amountMinor = 30_00, categoryId = household)))
        val hits = find(listOf(split), category = TransactionSearch.CategoryFilter.Is(household))!!
        assertEquals(listOf(TransactionSearch.Total("CHF", 30_00, 0)), TransactionSearch.totals(hits))
    }
}
