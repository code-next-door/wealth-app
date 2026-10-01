package io.github.codenextdoor.wealth.update

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import java.io.File

/** "A new version is out": shown over any screen when the daily check found one. */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val release = state.release ?: return
    val context = LocalContext.current

    // Downloaded and checked: Android's installer takes over (it asks to confirm, and
    // only accepts an update signed like this app).
    LaunchedEffect(state.apk) {
        state.apk?.let { apk ->
            viewModel.beforeLeavingApp()
            install(context, apk)
            viewModel.done()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.downloading) viewModel.later() },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_message, release.version, state.currentVersion.orEmpty()))
                when {
                    state.downloading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp))
                        Text(stringResource(R.string.update_downloading))
                    }
                    state.failed -> Text(
                        stringResource(R.string.update_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (state.failed) {
                TextButton(onClick = {
                    viewModel.beforeLeavingApp()
                    context.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri()))
                    viewModel.done()
                }) { Text(stringResource(R.string.update_open_page)) }
            } else {
                TextButton(onClick = viewModel::update, enabled = !state.downloading) { Text(stringResource(R.string.update_install)) }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::later, enabled = !state.downloading) { Text(stringResource(R.string.update_later)) } },
    )
}

/** Hands the checked APK to Android's installer (it asks once to allow installs from Wealth). */
private fun install(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Settings › Updates: the daily check's switch, "Check now" and this version. */
@Composable
fun UpdatesSection(viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val upToDate = stringResource(R.string.update_up_to_date)
    LaunchedEffect(state.checkedNow) {
        // Found: the dialog shows; otherwise say so.
        if (state.checkedNow == false) messages.show(upToDate)
        if (state.checkedNow != null) viewModel.checkedNowShown()
    }
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(
        headlineContent = { Text(stringResource(R.string.update_check_daily)) },
        supportingContent = {
            Text(stringResource(if (state.canCheck) R.string.update_check_daily_summary else R.string.update_not_a_release))
        },
        trailingContent = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled, enabled = state.canCheck) },
        colors = colors,
        modifier = Modifier.clickable(enabled = state.canCheck) { viewModel.setEnabled(!state.enabled) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.update_check_now)) },
        supportingContent = { Text(stringResource(R.string.update_version, state.currentVersion ?: stringResource(R.string.update_version_dev))) },
        colors = colors,
        modifier = Modifier.clickable(enabled = state.canCheck, onClick = viewModel::checkNow),
    )
}
