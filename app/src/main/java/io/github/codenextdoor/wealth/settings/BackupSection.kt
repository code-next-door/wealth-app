package io.github.codenextdoor.wealth.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private const val MIN_PASSWORD_LENGTH = 8

/** Export and restore rows for the settings screen, with their dialogs and file pickers. */
@Composable
fun BackupSection(viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Passwords live only in memory (not saved state) and only while needed.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) viewModel.export(uri, password)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    val messages = mapOf(
        BackupMessage.EXPORTED to stringResource(R.string.backup_exported),
        BackupMessage.RESTORED to stringResource(R.string.backup_restored),
        BackupMessage.WRONG_PASSWORD to stringResource(R.string.backup_wrong_password),
        BackupMessage.NOT_A_BACKUP to stringResource(R.string.backup_not_a_backup),
        BackupMessage.NEWER_VERSION to stringResource(R.string.backup_newer_version),
        BackupMessage.FAILED to stringResource(R.string.backup_failed),
    )
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, messages.getValue(it), Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_export)) },
        supportingContent = { Text(stringResource(R.string.backup_export_summary)) },
        trailingContent = { if (state.busy) CircularProgressIndicator(Modifier.padding(4.dp)) },
        colors = itemColors,
        modifier = Modifier.clickable(enabled = !state.busy) { askExportPassword = true },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_restore)) },
        supportingContent = { Text(stringResource(R.string.backup_restore_summary)) },
        colors = itemColors,
        modifier = Modifier.clickable(enabled = !state.busy) {
            viewModel.beforeFilePicker()
            openFile.launch(arrayOf("*/*"))
        },
    )

    if (askExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_choose_password),
            message = stringResource(R.string.backup_choose_password_message),
            confirm = true,
            onDone = { password ->
                askExportPassword = false
                exportPassword = password
                viewModel.beforeFilePicker()
                createFile.launch("wealth-backup-${LocalDate.now()}.wealthbackup")
            },
            onDismiss = { askExportPassword = false },
        )
    }

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

    state.pendingRestore?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_message,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.createdAt)),
                        summary.accounts,
                        summary.balanceEntries,
                        summary.expenses,
                        summary.rules,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_confirm_replace), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Password entry; with [confirm], asks twice and requires a minimum length. */
@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirm: Boolean,
    onDone: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = confirm && password.length < MIN_PASSWORD_LENGTH
    val mismatch = confirm && repeat.isNotEmpty() && repeat != password
    val valid = password.isNotEmpty() && !tooShort && (!confirm || repeat == password)
    val passwordKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    supportingText = if (confirm) ({ Text(stringResource(R.string.backup_password_hint, MIN_PASSWORD_LENGTH)) }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = passwordKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(stringResource(R.string.backup_password_mismatch)) }) else null,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = passwordKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(password.toCharArray()) }, enabled = valid) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
