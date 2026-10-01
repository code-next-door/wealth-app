package io.github.codenextdoor.wealth.data.backup

import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Backups are the author's safety net: every format ever written must still
 * restore exactly, and phones on an older app version must read new backups.
 * resources/backup/format-N.json are backups as each format wrote them (the
 * phone's org.json style: compact, "/" escaped), made from [sampleSnapshot].
 */
class BackupFormatsTest {

    private fun file(format: Int): String =
        javaClass.getResourceAsStream("/backup/format-$format.json")!!.use { it.readBytes().decodeToString() }

    /** What each format holds: fields added later read as their "none" value. */
    private fun expected(format: Int): BackupSnapshot {
        var s = sampleSnapshot
        if (format < 9) {
            s = s.copy(loans = emptyList(), loanRateChanges = emptyList(), accountTypes = s.accountTypes.map { it.copy(isLoan = false) })
        }
        if (format < 8) s = s.copy(expenseCategories = s.expenseCategories.map { it.copy(isIncome = false) }, removedImports = emptyList())
        if (format < 7) s = s.copy(expenseCategories = s.expenseCategories.map { it.copy(countsAsSpending = true) })
        if (format < 6) s = s.copy(accounts = s.accounts.map { it.copy(excludedFromNetWorth = false) })
        if (format < 5) s = s.copy(properties = emptyList())
        if (format < 4) s = s.copy(recurringExpenses = emptyList(), expenses = s.expenses.map { it.copy(recurringId = null) })
        if (format < 3) {
            s = s.copy(
                sharePrices = emptyList(),
                grants = emptyList(),
                accountTypes = s.accountTypes.map { it.copy(holdsShares = false) },
                accounts = s.accounts.map { it.copy(shareSymbol = null, units = null) },
                balanceEntries = s.balanceEntries.map { it.copy(units = null) },
            )
        }
        if (format < 2) s = s.copy(exchangeRates = s.exchangeRates.map { it.copy(source = ExchangeRateEntity.MANUAL) })
        return s
    }

    @Test
    fun readsEveryFormatWrittenSoFar() {
        for (format in 1..BackupSnapshot.FORMAT_VERSION) {
            assertEquals("format $format", expected(format), BackupSnapshot.fromJson(file(format)))
        }
    }

    @Test
    fun theFrozenOldReaderAgreesOnTheFormatsItKnows() {
        for (format in 1..5) {
            assertEquals("format $format", expected(format), LegacyBackupReader.fromJson(file(format)))
        }
    }

    @Test
    fun theOldReaderSaysANewerBackupNeedsAnUpdate() {
        // v0.2.2 knows formats up to 5: it asks for an update instead of misreading.
        assertThrows(BackupSnapshot.Companion.UnsupportedBackup::class.java) { LegacyBackupReader.fromJson(sampleSnapshot.toJson()) }
    }

    @Test
    fun theLayoutIsUnchangedApartFromNewFields() {
        // The same JSON, read by the pre-kotlinx reader: only fields it doesn't know are skipped.
        val asFormat5 = sampleSnapshot.toJson().replace("\"format\":${BackupSnapshot.FORMAT_VERSION}", "\"format\":5")
        assertEquals(expected(5), LegacyBackupReader.fromJson(asFormat5))
    }
}
