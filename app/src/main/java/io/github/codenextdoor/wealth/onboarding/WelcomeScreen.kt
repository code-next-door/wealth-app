package io.github.codenextdoor.wealth.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import io.github.codenextdoor.wealth.settings.BackupMessage
import io.github.codenextdoor.wealth.settings.BackupMessages
import io.github.codenextdoor.wealth.settings.BackupViewModel
import io.github.codenextdoor.wealth.settings.rememberRestoreFromBackup
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

@Composable
fun WelcomeRoute(
    viewModel: WelcomeViewModel,
    backup: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backup.state.collectAsStateWithLifecycle()
    // Reinstalled: the backup brings everything back, so straight into the app (no tour).
    LaunchedEffect(backupState.message) {
        if (backupState.message == BackupMessage.RESTORED) viewModel.finish(showTour = false)
    }
    Box(Modifier.fillMaxSize()) {
        WelcomeScreen(
            state = state,
            onBaseCurrency = viewModel::chooseBaseCurrency,
            onShowAround = { viewModel.finish(showTour = true) },
            onSkip = { viewModel.finish(showTour = false) },
            onRestore = rememberRestoreFromBackup(backup),
            restoring = backupState.busy,
        )
        // Wrong password and the like show here; "restored" shows on the home screen next.
        if (backupState.message != BackupMessage.RESTORED) BackupMessages(backup)
        SnackbarHost(
            LocalAppMessages.current.hostState,
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )
    }
}

/** Shown once, on a fresh install: what Wealth is, and the currency to add everything up in. */
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    onBaseCurrency: (String) -> Unit,
    onShowAround: () -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit = {},
    restoring: Boolean = false,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(112.dp),
            )
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Point(R.drawable.ic_lock, stringResource(R.string.welcome_private))
                Point(R.drawable.ic_public, stringResource(R.string.welcome_currencies))
                Point(R.drawable.ic_upload_file, stringResource(R.string.welcome_statements))
            }
            Spacer(Modifier.height(32.dp))
            DropdownField(
                label = stringResource(R.string.welcome_base_currency),
                options = state.currencies.map { DropdownOption(it.code, "${it.code} · ${it.name}") },
                selected = state.baseCurrency,
                onSelect = onBaseCurrency,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.welcome_base_currency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onShowAround, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_tour))
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_skip))
            }
            // Reinstalled? The data went with the old install; a backup file brings it back.
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.welcome_restore_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onRestore, enabled = !restoring, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_restore))
            }
        }
    }
}

@Composable
private fun Point(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    WealthTheme {
        WelcomeScreen(
            state = WelcomeUiState(listOf(Currency("CHF", "Swiss Franc", 2), Currency("INR", "Indian Rupee", 2)), "CHF"),
            onBaseCurrency = {}, onShowAround = {}, onSkip = {},
        )
    }
}
