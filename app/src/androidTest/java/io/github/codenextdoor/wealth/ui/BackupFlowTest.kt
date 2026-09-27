package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Encrypted backup through the real UI, with the file pickers stubbed. */
@RunWith(AndroidJUnit4::class)
class BackupFlowTest : UiTest() {

    private val backupFile by lazy { cacheFile("ui-test.wealthbackup") }

    @After
    fun deleteBackup() {
        backupFile.delete()
    }

    private fun enterPasswords(password: String, repeat: String? = password) {
        typeInto("Password", password)
        if (repeat != null) typeInto("Repeat password", repeat)
        rule.onNodeWithText("OK").performClick()
    }

    private fun accountNames() = runBlocking { container.accountRepository.accounts.first().map { it.name } }

    @Test
    fun exportThenRestoreBringsDataBack() {
        val name = "Backup test ${System.nanoTime() % 10000}"
        val id = addBankAccount(name)
        stubCreateDocument(backupFile)
        stubOpenDocument(backupFile)

        // Export.
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Export encrypted backup").performClick()
        enterPasswords("correct horse battery")
        waitForText("Backup saved", timeoutMs = 30_000) // key derivation is deliberately slow

        val bytes = backupFile.readBytes()
        assertTrue("not a Wealth backup", String(bytes.copyOf(6), Charsets.US_ASCII) == "WLTHBK")
        assertFalse("data readable in the file", String(bytes, Charsets.ISO_8859_1).contains(name))

        // Lose the account, then restore with a wrong password first.
        runBlocking { container.accountRepository.delete(id) }
        assertFalse(name in accountNames())

        rule.onNodeWithText("Restore from backup").performClick()
        waitForText("Backup password")
        enterPasswords("wrong password", repeat = null)
        waitForText("Wrong password, or the file is damaged", timeoutMs = 30_000)
        assertFalse(name in accountNames())

        // Right password: summary, confirm, restored.
        rule.onNodeWithText("Restore from backup").performClick()
        waitForText("Backup password")
        enterPasswords("correct horse battery", repeat = null)
        waitForText("Replace all data?", timeoutMs = 30_000)
        rule.onNodeWithText("Replace").performClick()
        waitForText("Backup restored")
        assertTrue(name in accountNames())
    }

    @Test
    fun restoringAFileThatIsNotABackupIsRejected() {
        backupFile.writeText("Date;Amount\n2026-01-01;-1.00")
        stubOpenDocument(backupFile)
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Restore from backup").performClick()
        waitForText("Backup password")
        enterPasswords("anything at all", repeat = null)
        waitForText("This isn't a Wealth backup file")
    }
}
