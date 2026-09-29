package io.github.codenextdoor.wealth.security

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupSnapshot
import io.github.codenextdoor.wealth.data.db.DatabaseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecoveryViewModelTest {

    private val backup = BackupSnapshot(1_790_000_000_000, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private var restored: BackupSnapshot? = null
    private var emptied = false
    private var readResult: () -> BackupSnapshot = { backup }
    private var retryResult: DatabaseState = DatabaseState.Unreadable(DatabaseState.Reason.KEY_UNAVAILABLE)

    private val vm by lazy {
        RecoveryViewModel(
            readBackup = { _, _ -> readResult() },
            restore = { restored = it },
            startEmpty = { emptied = true },
            retry = { retryResult },
            beforePicker = {},
        )
    }

    @Before fun main() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun reset() = Dispatchers.resetMain()

    @Test
    fun aWrongPasswordChangesNothing() {
        readResult = { throw BackupCrypto.WrongPasswordOrDamaged() }
        vm.read(Uri.EMPTY, "nope".toCharArray())
        assertEquals(RecoveryMessage.WRONG_PASSWORD, vm.state.value.message)
        assertNull(vm.state.value.pendingRestore)
        assertNull(restored)
    }

    @Test
    fun aBackupIsReadFirstAndRestoredOnlyOnConfirm() {
        vm.read(Uri.EMPTY, "secret".toCharArray())
        assertEquals(1_790_000_000_000, vm.state.value.pendingRestore!!.createdAt)
        assertNull(restored) // nothing moved yet
        vm.confirmRestore()
        assertEquals(backup, restored)
        assertNull(vm.state.value.pendingRestore)
    }

    @Test
    fun startFreshAndTryAgain() {
        vm.tryAgain()
        assertEquals(RecoveryMessage.STILL_UNREADABLE, vm.state.value.message)
        vm.messageShown()
        retryResult = DatabaseState.Ready
        vm.tryAgain()
        assertNull(vm.state.value.message)
        vm.startFresh()
        assertTrue(emptied)
    }
}
