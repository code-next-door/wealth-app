package io.github.codenextdoor.wealth.security

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.backup.BackupSnapshot
import io.github.codenextdoor.wealth.data.backup.BackupSummary
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecoveryMessage { WRONG_PASSWORD, NOT_A_BACKUP, NEWER_VERSION, FAILED, STILL_UNREADABLE }

data class RecoveryUiState(
    val busy: Boolean = false,
    val message: RecoveryMessage? = null,
    /** A backup that was read successfully, waiting for "Restore". */
    val pendingRestore: BackupSummary? = null,
)

/**
 * The recovery screen, shown when the database's key is gone: restore a backup into
 * a new database, start with an empty one, or try opening again. The unreadable data
 * is set aside, never deleted, and nothing moves before a backup has been read.
 */
class RecoveryViewModel(
    private val readBackup: suspend (Uri, CharArray) -> BackupSnapshot,
    private val restore: suspend (BackupSnapshot) -> Unit,
    private val startEmpty: suspend () -> Unit,
    private val retry: suspend () -> DatabaseState,
    private val beforePicker: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    private var pendingSnapshot: BackupSnapshot? = null

    fun beforeFilePicker() = beforePicker()

    /** Reads (and checks) the backup first; nothing changes until [confirmRestore]. */
    fun read(uri: Uri, password: CharArray) = run {
        val snapshot = readBackup(uri, password)
        pendingSnapshot = snapshot
        _state.update { it.copy(pendingRestore = BackupRepository.summarize(snapshot)) }
        null
    }

    fun confirmRestore() {
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null
        _state.update { it.copy(pendingRestore = null) }
        run {
            restore(snapshot)
            null
        }
    }

    fun cancelRestore() {
        pendingSnapshot = null
        _state.update { it.copy(pendingRestore = null) }
    }

    fun startFresh() = run {
        startEmpty()
        null
    }

    fun tryAgain() = run {
        if (retry() is DatabaseState.Unreadable) RecoveryMessage.STILL_UNREADABLE else null
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun run(work: suspend () -> RecoveryMessage?) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                work()
            } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                RecoveryMessage.WRONG_PASSWORD
            } catch (e: BackupCrypto.NotABackup) {
                RecoveryMessage.NOT_A_BACKUP
            } catch (e: BackupSnapshot.Companion.UnsupportedBackup) {
                RecoveryMessage.NEWER_VERSION
            } catch (e: Exception) {
                RecoveryMessage.FAILED
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c ->
            RecoveryViewModel(
                readBackup = c.backupFileReader::read,
                restore = c::recoverFromBackup,
                startEmpty = c::recoverWithEmptyDatabase,
                retry = c::openDatabase,
                beforePicker = c.appLock::allowBriefExit,
            )
        }
    }
}
