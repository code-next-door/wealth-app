package io.github.codenextdoor.wealth.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.backup.AndroidBackupFiles
import io.github.codenextdoor.wealth.data.backup.AutoBackup
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.preferences.AutoBackupPreferences
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.security.FakeSecretBox
import io.github.codenextdoor.wealth.settings.AutoBackupMessage
import io.github.codenextdoor.wealth.settings.AutoBackupViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AutoBackupViewModelTest : DatabaseTest() {

    private val file by lazy { File(context.cacheDir, "auto-backup-vm-test.wealthbackup") }
    private val other by lazy { File(context.cacheDir, "auto-backup-vm-other.wealthbackup") }

    @After
    fun deleteFiles() {
        file.delete()
        other.delete()
    }

    private val onboarding by lazy { OnboardingPreferences(SettingsStore(context, "onboarding_autovm_${System.nanoTime()}")) }
    private val prefs by lazy { AutoBackupPreferences(SettingsStore(context, "autobackup_vm_${System.nanoTime()}"), FakeSecretBox()) }

    private fun viewModel() = AutoBackupViewModel(
        AutoBackup(BackupRepository(db, context), prefs, AndroidBackupFiles(context), iterations = 1_000, onBackedUp = onboarding::setBackupMade),
        prefs,
        AppLock(SettingsStore(context, "lock_autovm_${System.nanoTime()}")),
    ).cancelledAfterTest()

    private fun decrypts(file: File, password: String) = runCatching { BackupCrypto.decrypt(file.readBytes(), password.toCharArray()) }.isSuccess

    @Test
    fun turningOnBacksUpRightAwayAndTicksGettingStarted() {
        addAccount("Savings")
        val vm = viewModel()
        vm.turnOn(Uri.fromFile(file), "long password".toCharArray())
        assertEquals(AutoBackupMessage.BACKED_UP, vm.state.await { it.message != null }.message)
        assertTrue(vm.state.value.settings.on)
        assertTrue(decrypts(file, "long password"))
        assertTrue(onboarding.backupMade.value)
    }

    @Test
    fun anotherFileAndPasswordTakeEffectAtOnce() {
        val vm = viewModel()
        vm.turnOn(Uri.fromFile(file), "long password".toCharArray())
        vm.state.await { it.message != null }
        vm.messageShown()

        vm.changeFile(Uri.fromFile(other))
        assertEquals(AutoBackupMessage.BACKED_UP, vm.state.await { it.message != null }.message)
        assertTrue(decrypts(other, "long password"))
        vm.messageShown()

        vm.changePassword("new long password".toCharArray())
        vm.state.await { it.message != null }
        assertTrue(decrypts(other, "new long password"))
    }

    @Test
    fun aFileThatCannotBeWrittenSaysSo() {
        val vm = viewModel()
        vm.turnOn(Uri.fromFile(File(context.cacheDir, "missing-folder/backup.wealthbackup")), "long password".toCharArray())
        assertEquals(AutoBackupMessage.FAILED, vm.state.await { it.message != null }.message)
        assertTrue(vm.state.value.settings.failedAt != null)
    }

    @Test
    fun turningOffForgetsIt() {
        val vm = viewModel()
        vm.turnOn(Uri.fromFile(file), "long password".toCharArray())
        vm.state.await { it.message != null }
        vm.turnOff()
        assertFalse(vm.state.await { !it.settings.on }.settings.on)
        assertEquals(null, prefs.password())
    }
}
