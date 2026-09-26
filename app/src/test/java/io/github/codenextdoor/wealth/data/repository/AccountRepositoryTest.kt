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
class AccountRepositoryTest : DatabaseTest() {

    private fun history(id: Long) = runBlocking { accounts.observeHistory(id).first() }

    @Test
    fun newAccountStartsItsHistory() {
        val id = addAccount("Salary", balanceMinor = 500_00)
        assertEquals(listOf(500_00L), history(id).map { it.balanceMinor })
        assertEquals(500_00L, runBlocking { accounts.get(id)!!.balanceMinor })
    }

    @Test
    fun editingDetailsWithoutBalanceChangeAddsNoHistory() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00, date = today.minusDays(10))
        val account = accounts.get(id)!!
        accounts.save(account.copy(name = "Main account"), balanceDate = today, recordBalance = false)
        assertEquals(1, history(id).size)
        assertEquals("Main account", accounts.get(id)!!.name)
    }

    @Test
    fun newBalanceIsAddedToHistoryAndBecomesCurrent() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00, date = today.minusDays(10))
        accounts.save(accounts.get(id)!!.copy(balanceMinor = 700_00), balanceDate = today, recordBalance = true)
        assertEquals(listOf(700_00L, 500_00L), history(id).map { it.balanceMinor }) // newest first
        assertEquals(700_00L, accounts.get(id)!!.balanceMinor)
    }

    @Test
    fun aPastBalanceDoesNotChangeTheCurrentOne() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00)
        accounts.addHistoryEntry(id, today.minusMonths(6), 100_00)
        assertEquals(500_00L, accounts.get(id)!!.balanceMinor)
        assertEquals(2, history(id).size)
    }

    @Test
    fun sameDayBalanceReplacesTheEntry() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00)
        accounts.addHistoryEntry(id, today, 800_00)
        assertEquals(listOf(800_00L), history(id).map { it.balanceMinor })
        assertEquals(800_00L, accounts.get(id)!!.balanceMinor)
    }

    @Test
    fun editingTheLatestEntryUpdatesTheCurrentBalance() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00)
        val entry = history(id).single()
        accounts.updateHistoryEntry(entry.id, today, 450_00)
        assertEquals(450_00L, accounts.get(id)!!.balanceMinor)
    }

    @Test
    fun movingAnEntryOntoAnotherDateReplacesThatDay() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00)
        accounts.addHistoryEntry(id, today.minusDays(5), 300_00)
        val old = history(id).single { it.balanceMinor == 300_00L }
        accounts.updateHistoryEntry(old.id, today, 350_00)
        assertEquals(listOf(350_00L), history(id).map { it.balanceMinor })
    }

    @Test
    fun theLastEntryCantBeDeleted() = runBlocking {
        val id = addAccount("Salary", balanceMinor = 500_00)
        accounts.addHistoryEntry(id, today.minusDays(5), 300_00)
        val (latest, older) = history(id)
        accounts.deleteHistoryEntry(latest.id)
        assertEquals(300_00L, accounts.get(id)!!.balanceMinor) // current falls back to the older entry
        accounts.deleteHistoryEntry(older.id)
        assertEquals(1, history(id).size) // refused
    }

    @Test
    fun deletingAnAccountDeletesItsHistoryButKeepsExpenses() = runBlocking {
        val id = addAccount("Salary")
        expenses.save(expense("Rent", 1000_00, accountId = id))
        accounts.delete(id)
        assertNull(accounts.get(id))
        assertTrue(accounts.balanceEntries.first().none { it.accountId == id })
        assertNull(expenses.expensesBetween(today, today).first().single().accountId)
    }
}
