package io.github.codenextdoor.wealth.settings

import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.backup.BackupCrypto
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.backup.BackupSnapshot
import io.github.codenextdoor.wealth.data.backup.BackupSummary
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupMessage { EXPORTED, RESTORED, WRONG_PASSWORD, NOT_A_BACKUP, NEWER_VERSION, FAILED }

data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    /** A decrypted backup waiting for the user to confirm replacing their data. */
    val pendingRestore: BackupSummary? = null,
)

class BackupViewModel(
    private val repository: BackupRepository,
    private val appLock: AppLock,
    private val onboarding: OnboardingPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var pendingSnapshot: BackupSnapshot? = null

    /** Opening the system file picker shouldn't trigger the app lock on return. */
    fun beforeFilePicker() = appLock.allowBriefExit()

    fun export(uri: Uri, password: CharArray) = run {
        repository.export(uri, password)
        onboarding.setBackupMade() // ticks "Make a backup" on the getting-started checklist
        BackupMessage.EXPORTED
    }

    fun read(uri: Uri, password: CharArray) = run {
        val snapshot = repository.read(uri, password)
        pendingSnapshot = snapshot
        _state.update { it.copy(pendingRestore = BackupRepository.summarize(snapshot)) }
        null
    }

    fun confirmRestore() {
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null
        _state.update { it.copy(pendingRestore = null) }
        run {
            repository.restore(snapshot)
            BackupMessage.RESTORED
        }
    }

    fun cancelRestore() {
        pendingSnapshot = null
        _state.update { it.copy(pendingRestore = null) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Runs [work] in the background, showing progress and turning failures into messages. */
    private fun run(work: suspend () -> BackupMessage?) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                work()
            } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                BackupMessage.WRONG_PASSWORD
            } catch (e: BackupCrypto.NotABackup) {
                BackupMessage.NOT_A_BACKUP
            } catch (e: BackupSnapshot.Companion.UnsupportedBackup) {
                BackupMessage.NEWER_VERSION
            } catch (e: Exception) {
                BackupMessage.FAILED
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory = appViewModelFactory { BackupViewModel(it.backupRepository, it.appLock, it.onboarding) }
    }
}
