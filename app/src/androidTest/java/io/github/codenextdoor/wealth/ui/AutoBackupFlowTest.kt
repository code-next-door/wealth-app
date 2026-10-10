package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupSnapshot
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Automatic backup through the real Settings UI, with the file picker stubbed. */
@RunWith(AndroidJUnit4::class)
class AutoBackupFlowTest : UiTest() {

    private val backupFile by lazy { cacheFile("ui-test-auto.wealthbackup") }

    @Before
    fun startOff() = container.autoBackup.turnOff()

    @After
    fun cleanUp() {
        container.autoBackup.turnOff()
        backupFile.delete()
        val before = container.autoBackupPreferences.state.value.lastSuccess
    }

    private fun backedUpAccounts(password: String) =
        BackupSnapshot.fromJson(String(BackupCrypto.decrypt(backupFile.readBytes(), password.toCharArray()))).accounts.map { it.name }

    private fun openRow() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Automatic backup").performScrollTo().performClick()
    }

    @Test
    fun turningOnBacksUpAtOnceAndBackUpNowWritesAgain() {
        val name = "Auto backup test ${System.nanoTime() % 10000}"
        addBankAccount(name)
        stubCreateDocument(backupFile)

        openRow()
        waitForText("Password for automatic backups")
        typeInto("Password", "correct horse battery")
        typeInto("Repeat password", "correct horse battery")
        rule.onNodeWithText("OK").performClick()
        waitForText("Automatic backup saved", timeoutMs = 30_000) // key derivation is deliberately slow
        waitForText("On · last backup", substring = true)
        assertTrue(name in backedUpAccounts("correct horse battery"))
        assertTrue(container.autoBackupPreferences.state.value.on)

        // While on, the row offers the other actions.
        backupFile.delete()
        val before = container.autoBackupPreferences.state.value.lastSuccess
        // The snackbar may cover the row: tap it through its click action.
        rule.onNode(hasText("Automatic backup") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Back up now").performClick()
        // The first "saved" snackbar may still show: wait for the new backup itself.
        rule.waitUntil(30_000) { container.autoBackupPreferences.state.value.lastSuccess != before }
        assertTrue(name in backedUpAccounts("correct horse battery"))
    }

    @Test
    fun aFileThatCannotBeWrittenShowsWhatToDo() {
        stubCreateDocument(java.io.File(context.cacheDir, "no-such-folder/backup.wealthbackup"))
        openRow()
        typeInto("Password", "correct horse battery")
        typeInto("Repeat password", "correct horse battery")
        rule.onNodeWithText("OK").performClick()
        waitForText("Couldn't write the backup file. Choose the file again.", timeoutMs = 30_000)
        waitForText("Tap to choose it again", substring = true)
        assertFalse(container.autoBackupPreferences.state.value.failedAt == null)
    }
}
