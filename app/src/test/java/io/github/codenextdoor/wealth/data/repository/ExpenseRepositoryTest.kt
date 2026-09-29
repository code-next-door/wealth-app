package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest : DatabaseTest() {

    private fun all() = runBlocking { expenses.expensesBetween(today.minusYears(1), today).first() }

    @Test
    fun saveUpdateAndDelete() = runBlocking {
        expenses.save(expense("Coffee", 4_50))
        val saved = all().single()
        expenses.save(saved.copy(amountMinor = 5_00, note = "big one"))
        assertEquals(5_00L, expenses.get(saved.id)!!.amountMinor)
        assertEquals("big one", expenses.get(saved.id)!!.note)
        expenses.delete(saved.id)
        assertTrue(all().isEmpty())
    }

    @Test
    fun earliestDateIsTheOldestExpense() = runBlocking {
        assertNull(expenses.earliestDate.first())
        expenses.save(expense("New", 1_00, date = today))
        expenses.save(expense("Old", 1_00, date = today.minusYears(2)))
        assertEquals(today.minusYears(2), expenses.earliestDate.first())
    }

    @Test
    fun monthQueryIncludesBoundaries() = runBlocking {
        val first = today.withDayOfMonth(1)
        expenses.save(expense("First day", 1_00, date = first))
        expenses.save(expense("Day before", 1_00, date = first.minusDays(1)))
        assertEquals(listOf("First day"), expenses.expensesBetween(first, first.plusMonths(1).minusDays(1)).first().map { it.description })
    }

    @Test
    fun importingSkipsKeysAlreadyImported() = runBlocking {
        val first = listOf(expense("A", 1_00) to "k1", expense("B", 2_00) to "k2")
        assertEquals(2, expenses.importExpenses(first))
        assertEquals(1, expenses.importExpenses(listOf(expense("B", 2_00) to "k2", expense("C", 3_00) to "k3")))
        assertEquals(setOf("k1", "k3"), expenses.existingImportKeys(listOf("k1", "k3", "k9")))
        assertEquals(3, all().size)
    }

    @Test
    fun rulesAreNormalizedAndReplaceSameKeyword() = runBlocking {
        expenses.saveRule(null, "  café du coin ", categoryId("eating_out"))
        assertTrue(expenses.rules().any { it.keyword == "CAFE DU COIN" })
        expenses.saveRule(null, "Cafe du Coin", categoryId("groceries"))
        assertEquals(categoryId("groceries"), expenses.rules().single { it.keyword == "CAFE DU COIN" }.categoryId)
    }

    @Test
    fun editingARuleKeywordReplacesTheOldRule() = runBlocking {
        expenses.saveRule(null, "OLDNAME", categoryId("travel"))
        val rule = expenses.rules().single { it.keyword == "OLDNAME" }
        expenses.saveRule(rule.id, "NEWNAME", categoryId("travel"))
        val keywords = expenses.rules().map { it.keyword }
        assertTrue("NEWNAME" in keywords && "OLDNAME" !in keywords)
    }

    @Test
    fun reapplyingRulesChangesOnlyUnlockedExpenses() = runBlocking {
        val groceries = categoryId("groceries")
        val shopping = categoryId("shopping")
        expenses.save(expense("MIGROS ZURICH", 10_00)) // uncategorized, unlocked
        expenses.save(expense("MIGROS BERN", 20_00, categoryId = shopping, locked = true)) // user's choice
        expenses.save(expense("UBS CARD CENTER", 500_00, categoryId = shopping, locked = true)) // user's choice too
        assertEquals(1, expenses.reapplyRules())
        val byName = all().associateBy { it.description }
        assertEquals(groceries, byName.getValue("MIGROS ZURICH").categoryId)
        assertEquals(shopping, byName.getValue("MIGROS BERN").categoryId)
        assertEquals(shopping, byName.getValue("UBS CARD CENTER").categoryId)
    }

    @Test
    fun deletingARuleLeavesExpensesAsTheyAre() = runBlocking {
        expenses.save(expense("MIGROS", 10_00))
        expenses.reapplyRules()
        expenses.deleteRule(expenses.rules().single { it.keyword == "MIGROS" }.id)
        assertEquals(categoryId("groceries"), all().single().categoryId)
        expenses.reapplyRules()
        assertNull(all().single().categoryId)
    }
}
