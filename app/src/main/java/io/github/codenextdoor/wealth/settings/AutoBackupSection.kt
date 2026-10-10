package io.github.codenextdoor.wealth.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.reminders.BalanceReminderWorker
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import java.text.DateFormat
import java.util.Date

private const val AUTO_BACKUP_FILE = "wealth-auto-backup.wealthbackup"

/**
 * Settings › Backup: the automatic backup row. Turning it on asks for a password, then
 * the file (once), and backs up right away; tapping it while on offers the other actions.
 */
@Composable
fun AutoBackupSection(viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    AutoBackupMessages(viewModel)

    // Passwords live only in memory (not saved state) and only while needed.
    var newPassword by remember { mutableStateOf<CharArray?>(null) }
    var askPassword by remember { mutableStateOf<PasswordFor?>(null) }
    var showOptions by remember { mutableStateOf(false) }

    // Failures are announced by a notification; ask for it once automatic backups are on.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = newPassword
        newPassword = null
        when {
            uri == null -> password?.fill(' ')
            password != null -> {
                viewModel.turnOn(uri, password)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BalanceReminderWorker.canNotify(context)) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> viewModel.changeFile(uri)
        }
    }
    fun chooseFile() {
        viewModel.beforeFilePicker()
        pickFile.launch(AUTO_BACKUP_FILE)
    }

    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val failedAt = settings.failedAt
    val lastSuccess = settings.lastSuccess
    val summary = when {
        !settings.on -> stringResource(R.string.auto_backup_summary_off)
        failedAt != null -> stringResource(R.string.auto_backup_summary_failed, dateFormat.format(Date(failedAt)))
        lastSuccess != null -> stringResource(R.string.auto_backup_summary_last, dateFormat.format(Date(lastSuccess)))
        else -> stringResource(R.string.auto_backup_summary_never)
    }
    val onTap = {
        when {
            !settings.on -> askPassword = PasswordFor.TURN_ON
            failedAt != null -> chooseFile()
            else -> showOptions = true
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.auto_backup)) },
        supportingContent = {
            Text(summary, color = if (settings.on && failedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (state.busy) {
                CircularProgressIndicator(Modifier.padding(4.dp))
            } else {
                Switch(
                    checked = settings.on,
                    onCheckedChange = { on -> if (on) askPassword = PasswordFor.TURN_ON else viewModel.turnOff() },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = !state.busy, onClick = onTap),
    )

    askPassword?.let { purpose ->
        PasswordDialog(
            title = stringResource(R.string.auto_backup_password_title),
            message = stringResource(R.string.auto_backup_password_message),
            confirm = true,
            onDone = { password ->
                askPassword = null
                when (purpose) {
                    PasswordFor.TURN_ON -> {
                        newPassword = password
                        chooseFile()
                    }
                    PasswordFor.CHANGE -> viewModel.changePassword(password)
                }
            },
            onDismiss = { askPassword = null },
        )
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(stringResource(R.string.auto_backup_options_title)) },
            text = {
                Column {
                    OptionButton(stringResource(R.string.auto_backup_now)) {
                        showOptions = false
                        viewModel.backUpNow()
                    }
                    OptionButton(stringResource(R.string.auto_backup_change_file)) {
                        showOptions = false
                        chooseFile()
                    }
                    OptionButton(stringResource(R.string.auto_backup_change_password)) {
                        showOptions = false
                        askPassword = PasswordFor.CHANGE
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOptions = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class PasswordFor { TURN_ON, CHANGE }

@Composable
private fun OptionButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AutoBackupMessages(viewModel: AutoBackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    val saved = stringResource(R.string.auto_backup_saved)
    val failed = stringResource(R.string.auto_backup_failed)
    LaunchedEffect(state.message) {
        state.message?.let {
            appMessages.show(if (it == AutoBackupMessage.BACKED_UP) saved else failed)
            viewModel.messageShown()
        }
    }
}
