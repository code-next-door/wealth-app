package io.github.codenextdoor.wealth.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest : DatabaseTest() {

    private fun fillWithData() {
        setRate("CHF", "INR", "105")
        setRate("CHF", "INR", "95", today.minusYears(1))
        val salary = addAccount("Salary", balanceMinor = 12_000_00)
        runBlocking {
            accounts.addHistoryEntry(salary, today.minusMonths(3), 9_000_00)
            catalog.addCountry("Germany")
            expenses.save(expense("MIGROS", 45_30, categoryId = categoryId("groceries"), accountId = salary))
            expenses.saveRule(null, "WISE", null)
        }
    }

    @Test
    fun backupRestoresIdenticallyOnAnotherPhone() = runBlocking {
        fillWithData()
        val repo = BackupRepository(db, context)
        val original = repo.snapshot()
        val file = BackupCrypto.encrypt(original.toJson().toByteArray(), "long password".toCharArray(), iterations = 1_000)

        // "Another phone": a fresh, empty database.
        val other = Room.inMemoryDatabaseBuilder(context, WealthDatabase::class.java).allowMainThreadQueries().build()
        try {
            val otherRepo = BackupRepository(other, context)
            otherRepo.restore(BackupSnapshot.fromJson(String(BackupCrypto.decrypt(file, "long password".toCharArray()))))
            assertEquals(original.copy(createdAt = 0), otherRepo.snapshot().copy(createdAt = 0))
        } finally {
            other.close()
        }
    }

    @Test
    fun restoreReplacesEverythingAndScreensUpdate() = runBlocking {
        fillWithData()
        val repo = BackupRepository(db, context)
        val backup = repo.snapshot()

        // Changes after the backup...
        addAccount("Added later")
        expenses.save(expense("Later", 1_00))
        repo.restore(backup)

        // ...are gone, and live data reflects the restore.
        assertTrue(accounts.accounts.first().none { it.name == "Added later" })
        assertEquals(listOf("MIGROS"), expenses.expensesBetween(today.minusYears(1), today).first().map { it.description })
        assertEquals(backup.copy(createdAt = 0), repo.snapshot().copy(createdAt = 0))
    }

    @Test
    fun summaryCountsWhatIsInside() = runBlocking {
        fillWithData()
        val summary = BackupRepository.summarize(BackupRepository(db, context).snapshot())
        assertEquals(1, summary.accounts)
        assertEquals(2, summary.balanceEntries)
        assertEquals(1, summary.expenses)
        assertTrue(summary.rules > 100)
    }
}
