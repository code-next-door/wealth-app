package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.ExpensePart
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun imported(key: String, amountMinor: Long, accountId: Long? = null, date: java.time.LocalDate = today) =
        expense("ROW $key", amountMinor, date = date, accountId = accountId) to key

    @Test
    fun aDeletedImportIsRememberedUntilImportedOnPurpose() = runBlocking {
        assertEquals(2, expenses.importExpenses(listOf(imported("a", 10_00), imported("b", 20_00))))
        expenses.delete(all().single { it.description == "ROW a" }.id)
        assertEquals(setOf("a"), expenses.removedImportKeys(listOf("a", "b")))
        assertTrue(expenses.existingImportKeys(listOf("a")).isEmpty())

        // Imported again on purpose (ticked on the Import screen): saved, and no longer "deleted".
        assertEquals(1, expenses.importExpenses(listOf(imported("a", 10_00))))
        assertTrue(expenses.removedImportKeys(listOf("a")).isEmpty())
    }

    @Test
    fun deletingAnExpenseTypedByHandRemembersNothing() = runBlocking {
        expenses.save(expense("Cash", 5_00))
        expenses.delete(all().single().id)
        assertTrue(db.backupDao().removedImports().isEmpty())
    }

    @Test
    fun sameDaySameAmountOnTheSameAccountIsAPossibleDuplicate() = runBlocking {
        val account = addAccount("Salary account")
        val other = addAccount("Other account")
        // Saved from an earlier file: two coffees and a lunch on the same day.
        expenses.importExpenses(
            listOf(imported("old1", 4_50, account), imported("old2", 4_50, account), imported("old3", 25_00, account)),
        )
        val candidates = listOf(
            ExpenseRepository.Candidate(0, today, 4_50), // coffee 1
            ExpenseRepository.Candidate(1, today, 4_50), // coffee 2
            ExpenseRepository.Candidate(2, today, 4_50), // a third coffee: new
            ExpenseRepository.Candidate(3, today.minusDays(1), 25_00), // same amount, another day: new
            ExpenseRepository.Candidate(4, today, 25_00), // lunch
        )
        assertEquals(setOf(0, 1, 4), expenses.possibleDuplicates(account, candidates, statementKeys = emptyList()))
        // Another account's expenses never match; neither does a file with no account.
        assertTrue(expenses.possibleDuplicates(other, candidates, emptyList()).isEmpty())
        assertTrue(expenses.possibleDuplicates(null, candidates, emptyList()).isEmpty())
        // Expenses that came from this very statement are its own rows, not duplicates.
        assertEquals(setOf(4), expenses.possibleDuplicates(account, candidates, statementKeys = listOf("old1", "old2")))
    }

    @Test
    fun moneyInAndMoneyOutOfTheSameSizeDontMatch() = runBlocking {
        val account = addAccount("Salary account")
        expenses.importExpenses(listOf(imported("out", 100_00, account)))
        val refund = ExpenseRepository.Candidate(0, today, -100_00)
        assertTrue(expenses.possibleDuplicates(account, listOf(refund), emptyList()).isEmpty())
    }

    @Test
    fun aSplitIsSavedEditedAndUndone() = runBlocking {
        val groceries = categoryId("groceries")
        val household = categoryId("shopping")
        expenses.save(expense("Migros", 120_00, categoryId = groceries))
        val saved = all().single()
        assertFalse(saved.categoryLocked)

        expenses.save(saved.copy(parts = listOf(ExpensePart(amountMinor = 30_00, categoryId = household, note = "detergent"))))
        val split = expenses.get(saved.id)!!
        assertEquals(listOf(30_00L to household), split.parts.map { it.amountMinor to it.categoryId })
        assertEquals("detergent", split.parts.single().note)
        assertEquals(90_00L, split.restMinor)
        assertTrue(split.categoryLocked) // split by hand: rules leave it alone
        assertEquals(split.parts, all().single().parts) // the month list has them too

        // Editing replaces the parts; no parts undoes the split.
        expenses.save(split.copy(parts = listOf(ExpensePart(amountMinor = 10_00, categoryId = household), ExpensePart(amountMinor = 5_00, categoryId = null))))
        assertEquals(listOf(10_00L, 5_00L), expenses.get(saved.id)!!.parts.map { it.amountMinor })
        expenses.save(expenses.get(saved.id)!!.copy(parts = emptyList()))
        assertTrue(expenses.get(saved.id)!!.parts.isEmpty())
        assertEquals(120_00L, expenses.get(saved.id)!!.amountMinor)
    }

    @Test
    fun deletingASplitExpenseDeletesItsParts() = runBlocking {
        expenses.save(expense("Migros", 120_00).copy(parts = listOf(ExpensePart(amountMinor = 30_00, categoryId = null))))
        val id = all().single().id
        expenses.delete(id)
        assertTrue(db.backupDao().expenseParts().isEmpty())
    }

    @Test
    fun aSplitImportedRowIsStillRecognisedWhenImportedAgain() = runBlocking {
        val row = expense("COOP-1234 ZUERICH", 80_00)
        assertEquals(1, expenses.importExpenses(listOf(row to "key|coop")))
        val saved = all().single()
        expenses.save(saved.copy(parts = listOf(ExpensePart(amountMinor = 20_00, categoryId = null))))
        // Same fingerprint: not added again, and the split stays.
        assertEquals(0, expenses.importExpenses(listOf(row to "key|coop")))
        assertEquals(setOf("key|coop"), expenses.existingImportKeys(listOf("key|coop")))
        assertEquals(1, expenses.get(saved.id)!!.parts.size)
    }
}
