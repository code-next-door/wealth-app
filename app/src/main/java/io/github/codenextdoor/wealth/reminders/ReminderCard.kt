package io.github.codenextdoor.wealth.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.BalanceReminders
import io.github.codenextdoor.wealth.ui.localDateFormat
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import java.time.LocalDate

/**
 * Overview: accounts whose latest balance is old, each opening its account; "Not now"
 * hides the card for a week. "Notify me" asks for the notification permission (Android
 * 13+) only when it's missing, so nobody gets a surprise prompt.
 */
@Composable
fun ReminderCard(stale: List<BalanceReminders.Stale>, onOpenAccount: (Long) -> Unit, onNotNow: () -> Unit) {
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(BalanceReminderWorker.canNotify(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = BalanceReminderWorker.canNotify(context)
    }
    val dateFormat = remember { localDateFormat("dMMMyyyy") }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.reminders_card_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            stale.forEach { account ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(account.accountId) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(account.accountName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.reminders_card_last, account.lastUpdated.format(dateFormat)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    TextButton(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.reminders_notify_me))
                    }
                }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.reminders_not_now)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReminderCardPreview() {
    WealthTheme {
        ReminderCard(
            stale = listOf(
                BalanceReminders.Stale(1, "Pillar 3a", LocalDate.of(2026, 8, 3)),
                BalanceReminders.Stale(2, "Brokerage", LocalDate.of(2026, 8, 31)),
            ),
            onOpenAccount = {},
            onNotNow = {},
        )
    }
}
