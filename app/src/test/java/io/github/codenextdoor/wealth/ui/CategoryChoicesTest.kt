package io.github.codenextdoor.wealth.ui

import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.components.categoryChoices
import io.github.codenextdoor.wealth.ui.components.fitsDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryChoicesTest {

    private val groceries = ExpenseCategory(1, "Groceries")
    private val salary = ExpenseCategory(2, "Salary", isIncome = true)
    private val transfers = ExpenseCategory(3, "Investments & transfers", countsAsSpending = false)
    private val other = ExpenseCategory(4, "Other income", isIncome = true)
    private val all = listOf(groceries, salary, transfers, other) // the order from settings

    @Test
    fun spentOffersOnlySpendingCategories() {
        // Counted or not: a transfer to the broker is still money out.
        assertEquals(listOf(groceries, transfers), categoryChoices(all, received = false).map { it.category })
    }

    @Test
    fun receivedOffersIncomeThenSpendingCategoriesAsRefunds() {
        val choices = categoryChoices(all, received = true)
        assertEquals(listOf(salary, other, groceries, transfers), choices.map { it.category })
        assertEquals(listOf(false, false, true, true), choices.map { it.asRefund })
    }

    @Test
    fun whatFitsWhichDirection() {
        assertFalse(fitsDirection(all, salary.id, received = false))
        assertTrue(fitsDirection(all, groceries.id, received = true))
        assertTrue(fitsDirection(all, null, received = false)) // uncategorized always fits
    }
}
