package io.github.codenextdoor.wealth.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.settings.BackupMessage
import io.github.codenextdoor.wealth.settings.BackupViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Restoring replaces everything, so it only happens after the user confirms the backup they picked. */
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest : DatabaseTest() {

    private val file by lazy { File(context.cacheDir, "backup-viewmodel-test.wealth") }

    @After
    fun deleteFile() {
        file.delete()
    }

    private fun viewModel() = BackupViewModel(
        BackupRepository(db, context),
        AppLock(SettingsStore(context, "lock_backupvm_${System.nanoTime()}")),
        OnboardingPreferences(SettingsStore(context, "onboarding_backupvm_${System.nanoTime()}")),
    ).cancelledAfterTest()

    /** A backup holding one account, "Backed up"; then the data moves on ("Added later"). */
    private fun backupThenChange(): Uri = runBlocking {
        addAccount("Backed up")
        val json = BackupRepository(db, context).snapshot().toJson()
        // Few key-derivation rounds keep the test fast; the file says how many it used.
        file.writeBytes(BackupCrypto.encrypt(json.toByteArray(), "long password".toCharArray(), iterations = 1_000))
        addAccount("Added later")
        Uri.fromFile(file)
    }

    private fun names() = runBlocking { accounts.accounts.first().map { it.name }.toSet() }

    @Test
    fun aWrongPasswordSaysSoAndChangesNothing() {
        val uri = backupThenChange()
        val vm = viewModel()
        vm.read(uri, "wrong".toCharArray())
        val state = vm.state.await { it.message != null }
        assertEquals(BackupMessage.WRONG_PASSWORD, state.message)
        assertNull(state.pendingRestore)
        assertEquals(setOf("Backed up", "Added later"), names())
    }

    @Test
    fun readingOnlyAsksAndCancellingKeepsTheData() {
        val uri = backupThenChange()
        val vm = viewModel()
        vm.read(uri, "long password".toCharArray())
        val pending = vm.state.await { it.pendingRestore != null }.pendingRestore!!
        assertEquals(1, pending.accounts) // what the backup holds, shown before replacing anything
        assertEquals(setOf("Backed up", "Added later"), names())

        vm.cancelRestore()
        assertNull(vm.state.value.pendingRestore)
        vm.confirmRestore() // nothing pending any more: does nothing
        assertEquals(setOf("Backed up", "Added later"), names())
    }

    @Test
    fun confirmingRestoresTheBackup() {
        val uri = backupThenChange()
        val vm = viewModel()
        vm.read(uri, "long password".toCharArray())
        vm.state.await { it.pendingRestore != null }
        vm.confirmRestore()
        assertEquals(BackupMessage.RESTORED, vm.state.await { it.message != null }.message)
        assertEquals(setOf("Backed up"), names())
        assertTrue(!vm.state.value.busy)
    }
}
