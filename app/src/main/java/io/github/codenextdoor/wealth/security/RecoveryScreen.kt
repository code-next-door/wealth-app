package io.github.codenextdoor.wealth.security

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.settings.PasswordDialog
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun RecoveryRoute(viewModel: RecoveryViewModel = viewModel(factory = RecoveryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val messages = mapOf(
        RecoveryMessage.WRONG_PASSWORD to stringResource(R.string.backup_wrong_password),
        RecoveryMessage.NOT_A_BACKUP to stringResource(R.string.backup_not_a_backup),
        RecoveryMessage.NEWER_VERSION to stringResource(R.string.backup_newer_version),
        RecoveryMessage.FAILED to stringResource(R.string.backup_failed),
        RecoveryMessage.STILL_UNREADABLE to stringResource(R.string.recovery_still_unreadable),
    )
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(messages.getValue(it))
            viewModel.messageShown()
        }
    }
    RecoveryScreen(
        state = state,
        snackbar = snackbar,
        onRestore = {
            viewModel.beforeFilePicker()
            openFile.launch(arrayOf("*/*"))
        },
        onStartFresh = viewModel::startFresh,
        onTryAgain = viewModel::tryAgain,
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
    )
    restoreUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.backup_enter_password),
            message = stringResource(R.string.backup_enter_password_message),
            confirm = false,
            onDone = { password ->
                restoreUri = null
                viewModel.read(uri, password)
            },
            onDismiss = { restoreUri = null },
        )
    }
}

/**
 * Shown instead of the app when its data can't be opened (the key that unlocks it is
 * gone). Restoring a backup brings everything back; starting fresh gives an empty app.
 * Either way the unreadable data is kept aside, never deleted.
 */
@Composable
fun RecoveryScreen(
    state: RecoveryUiState,
    snackbar: SnackbarHostState,
    onRestore: () -> Unit,
    onStartFresh: () -> Unit,
    onTryAgain: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    var confirmFresh by rememberSaveable { mutableStateOf(false) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.recovery_explanation), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.recovery_options), style = MaterialTheme.typography.bodyLarge)
            if (state.busy) CircularProgressIndicator()
            Button(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_restore))
            }
            OutlinedButton(onClick = { confirmFresh = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_start_fresh))
            }
            TextButton(onClick = onTryAgain, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_try_again))
            }
        }
    }
    if (confirmFresh) {
        AlertDialog(
            onDismissRequest = { confirmFresh = false },
            title = { Text(stringResource(R.string.recovery_start_fresh_title)) },
            text = { Text(stringResource(R.string.recovery_start_fresh_message)) },
            confirmButton = {
                TextButton(onClick = { confirmFresh = false; onStartFresh() }) {
                    Text(stringResource(R.string.recovery_start_fresh), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmFresh = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.pendingRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.recovery_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recovery_restore_message,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.createdAt)),
                        summary.accounts,
                        summary.balanceEntries,
                        summary.expenses,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.recovery_restore_confirm)) } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryScreenPreview() {
    WealthTheme {
        RecoveryScreen(RecoveryUiState(), SnackbarHostState(), {}, {}, {}, {}, {})
    }
}
