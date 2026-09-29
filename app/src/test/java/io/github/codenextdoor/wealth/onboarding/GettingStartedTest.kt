package io.github.codenextdoor.wealth.onboarding

import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.BalanceEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GettingStartedTest {

    private val account = Account(1, "Salary", 1, "CHF", null, 100_00, Instant.EPOCH, null, null)
    private fun entry(accountId: Long, date: LocalDate) = BalanceEntry(0, accountId, date, 100_00)
    private val day = LocalDate.of(2026, 3, 31)

    @Test
    fun nothingDoneOnAFreshInstall() {
        assertEquals(emptySet<GettingStartedStep>(), GettingStarted.done(emptyList(), emptyList(), lockEnabled = false, backupMade = false))
    }

    @Test
    fun historyNeedsTwoDaysOnTheSameAccount() {
        val one = GettingStarted.done(listOf(account), listOf(entry(1, day), entry(2, day.minusDays(1))), false, false)
        assertEquals(setOf(GettingStartedStep.ADD_ACCOUNT), one) // two accounts, one day each: no history yet
        val two = GettingStarted.done(listOf(account), listOf(entry(1, day), entry(1, day.minusMonths(1))), false, false)
        assertEquals(setOf(GettingStartedStep.ADD_ACCOUNT, GettingStartedStep.BUILD_HISTORY), two)
    }

    @Test
    fun lockAndBackupComeFromTheirSettings() {
        assertEquals(
            setOf(GettingStartedStep.APP_LOCK, GettingStartedStep.BACKUP),
            GettingStarted.done(emptyList(), emptyList(), lockEnabled = true, backupMade = true),
        )
    }
}
