package io.github.codenextdoor.wealth.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.backup.AutoBackup
import io.github.codenextdoor.wealth.data.preferences.AutoBackupPreferences
import io.github.codenextdoor.wealth.data.preferences.AutoBackupState
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AutoBackupMessage { BACKED_UP, FAILED }

data class AutoBackupUiState(
    val settings: AutoBackupState = AutoBackupState(),
    val busy: Boolean = false,
    val message: AutoBackupMessage? = null,
)

/** Settings › Backup › Automatic backup: on/off, its file and password, "Back up now". */
class AutoBackupViewModel(
    private val autoBackup: AutoBackup,
    private val prefs: AutoBackupPreferences,
    private val appLock: AppLock,
) : ViewModel() {

    private val progress = MutableStateFlow(AutoBackupUiState())

    val state: StateFlow<AutoBackupUiState> = combine(prefs.state, progress) { settings, p -> p.copy(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupUiState(prefs.state.value))

    /** Opening the system file picker shouldn't trigger the app lock on return. */
    fun beforeFilePicker() = appLock.allowBriefExit()

    /** Starts with a backup right away, so a wrong file shows up now, not tomorrow. */
    fun turnOn(file: Uri, password: CharArray) = backUp {
        autoBackup.turnOn(file, password)
        password.fill(' ')
    }

    fun turnOff() = autoBackup.turnOff()

    fun changeFile(file: Uri) = backUp { autoBackup.changeFile(file) }

    fun changePassword(password: CharArray) = backUp {
        prefs.setPassword(password)
        password.fill(' ')
    }

    fun backUpNow() = backUp {}

    fun messageShown() = progress.update { it.copy(message = null) }

    private fun backUp(before: () -> Unit) {
        progress.update { it.copy(busy = true) }
        viewModelScope.launch {
            before()
            val outcome = autoBackup.run(force = true)
            val message = if (outcome == AutoBackup.Outcome.DONE) AutoBackupMessage.BACKED_UP else AutoBackupMessage.FAILED
            progress.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory = appViewModelFactory { container ->
            AutoBackupViewModel(container.autoBackup, container.autoBackupPreferences, container.appLock)
        }
    }
}
