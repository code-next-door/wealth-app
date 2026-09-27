package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.RecurringExpense
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest : DatabaseTest() {

    private val recurring by lazy { RecurringRepository(db) }

    private fun rent(start: LocalDate, end: LocalDate? = null) =
        RecurringExpense(0, "Rent", 2_000_00, "CHF", categoryId("housing"), null, 1, start, end, null)

    private fun added() = runBlocking { expenses.expensesBetween(today.minusYears(2), today.plusYears(1)).first() }

    @Test
    fun pastAndDueEntriesAreAddedOnceWithTheirCategory() = runBlocking {
        val id = recurring.save(rent(today.minusMonths(2)))
        assertEquals(3, recurring.addDue(today))
        assertEquals(0, recurring.addDue(today)) // nothing twice
        val rows = added()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.recurringId == id && it.categoryId == categoryId("housing") && it.categoryLocked && it.amountMinor == 2_000_00L })
        assertEquals(today, recurring.get(id)!!.lastAdded)

        assertEquals(1, recurring.addDue(today.plusMonths(1))) // a month later
    }

    @Test
    fun endedAndFutureOnesAddNothingMore() = runBlocking {
        recurring.save(rent(today.minusMonths(6), end = today.minusMonths(5)))
        recurring.save(rent(today.plusDays(3)))
        assertEquals(2, recurring.addDue(today))
    }

    @Test
    fun editingKeepsWhatWasAddedAndDeletingKeepsTheExpenses() = runBlocking {
        val id = recurring.save(rent(today.minusMonths(1)))
        recurring.addDue(today)
        val saved = recurring.get(id)!!
        recurring.save(saved.copy(amountMinor = 2_100_00)) // e.g. rent went up
        assertEquals(0, recurring.addDue(today)) // already-added months aren't added again
        assertEquals(today, recurring.get(id)!!.lastAdded)

        recurring.delete(id)
        assertTrue(recurring.recurring.first().isEmpty())
        assertEquals(2, added().size)
        assertTrue(added().all { it.recurringId == null })
    }

    @Test
    fun editingAnImportedExpenseKeepsItsImportFingerprint() = runBlocking {
        expenses.importExpenses(listOf(expense("COOP", 10_00) to "key-1"))
        val imported = added().single()
        expenses.save(imported.copy(categoryId = categoryId("groceries"), categoryLocked = true))
        assertEquals(setOf("key-1"), expenses.existingImportKeys(listOf("key-1"))) // still known: won't import twice
    }

    @Test
    fun aCurrencyUsedByARecurringExpenseCantBeDeleted() = runBlocking {
        recurring.save(rent(today.plusMonths(1)).copy(currencyCode = "USD"))
        assertFalse(currencies.deleteCurrency("USD"))
        assertNull(recurring.recurring.first().single().lastAdded)
    }
}
