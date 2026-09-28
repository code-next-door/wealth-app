package io.github.codenextdoor.wealth.data.backup

import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import org.junit.Assert.assertEquals
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
    fun theFrozenOldReaderAgreesOnEveryFormat() {
        for (format in 1..BackupSnapshot.FORMAT_VERSION) {
            assertEquals("format $format", expected(format), LegacyBackupReader.fromJson(file(format)))
        }
    }

    @Test
    fun anAppFromBeforeThisReaderCanRestoreNewBackups() {
        assertEquals(sampleSnapshot, LegacyBackupReader.fromJson(sampleSnapshot.toJson()))
    }
}
