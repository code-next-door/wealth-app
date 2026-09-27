package io.github.codenextdoor.wealth.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Short messages ("Backup saved") shown as a Material snackbar. App-wide, so
 * a message can outlive the screen that sent it: an import finishes, its
 * screen closes, and the message appears on the Spending tab.
 */
class AppMessages(val hostState: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        scope.launch { hostState.showSnackbar(message) }
    }
}

val LocalAppMessages = staticCompositionLocalOf<AppMessages> { error("AppMessages not provided") }
