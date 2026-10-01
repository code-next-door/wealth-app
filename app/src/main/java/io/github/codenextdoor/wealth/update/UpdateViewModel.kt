package io.github.codenextdoor.wealth.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.update.ApkDownloader
import io.github.codenextdoor.wealth.data.update.Release
import io.github.codenextdoor.wealth.data.update.UpdateChecker
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    /** A newer release to offer; null: no dialog. Not while the app is locked. */
    val release: Release? = null,
    val currentVersion: String? = null,
    val downloading: Boolean = false,
    /** The download failed or didn't match its checksum: offer the release page instead. */
    val failed: Boolean = false,
    /** Downloaded and checked: ready for Android's installer. */
    val apk: File? = null,
    // Settings.
    val enabled: Boolean = true,
    /** Builds that aren't releases (local, tests) never look. */
    val canCheck: Boolean = false,
    /** Set after "Check now": whether a newer version was found (false: up to date or offline). */
    val checkedNow: Boolean? = null,
)

/** The update dialog (any screen) and Settings › Updates. */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val appLock: AppLock,
) : ViewModel() {

    private data class Progress(val downloading: Boolean = false, val failed: Boolean = false, val apk: File? = null, val checkedNow: Boolean? = null)

    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<UpdateUiState> = combine(checker.available, checker.enabled, appLock.isLocked, progress) { release, enabled, locked, p ->
        UpdateUiState(
            release = release.takeIf { !locked },
            currentVersion = checker.currentVersion,
            downloading = p.downloading,
            failed = p.failed,
            apk = p.apk,
            enabled = enabled,
            canCheck = checker.currentVersion != null,
            checkedNow = p.checkedNow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateUiState())

    /** Downloads the APK and checks it; the screen then hands it to Android's installer. */
    fun update() {
        val release = checker.available.value ?: return
        progress.value = Progress(downloading = true)
        viewModelScope.launch {
            val apk = downloader.download(release)
            progress.value = if (apk == null) Progress(failed = true) else Progress(apk = apk)
        }
    }

    /** The installer (or the release page) opens outside the app: that shouldn't lock it. */
    fun beforeLeavingApp() = appLock.allowBriefExit()

    /** Handed to the installer (or the page opened): the dialog closes. */
    fun done() {
        progress.value = Progress()
        checker.dismiss()
    }

    fun later() = done()

    fun setEnabled(on: Boolean) = checker.setEnabled(on)

    fun checkNow() {
        viewModelScope.launch {
            val found = checker.checkNow() != null
            progress.update { it.copy(checkedNow = found) }
        }
    }

    fun checkedNowShown() = progress.update { it.copy(checkedNow = null) }

    companion object {
        val Factory = appViewModelFactory { UpdateViewModel(it.updateChecker, it.apkDownloader, it.appLock) }
    }
}
