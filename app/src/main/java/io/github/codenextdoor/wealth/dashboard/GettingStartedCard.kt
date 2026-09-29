package io.github.codenextdoor.wealth.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.onboarding.GettingStartedStep
import io.github.codenextdoor.wealth.onboarding.GettingStartedUiState
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

/** The first-run checklist on the Overview: each step ticks itself off; tapping one goes there. */
@Composable
fun GettingStartedCard(
    state: GettingStartedUiState,
    onStep: (GettingStartedStep) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.getting_started_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.getting_started_progress, state.done.size, GettingStartedStep.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onHide) { Text(stringResource(R.string.getting_started_hide)) }
        }
        GettingStartedStep.entries.forEach { step ->
            val done = step in state.done
            val (title, hint) = when (step) {
                GettingStartedStep.ADD_ACCOUNT -> R.string.getting_started_account to R.string.getting_started_account_hint
                GettingStartedStep.BUILD_HISTORY -> R.string.getting_started_history to R.string.getting_started_history_hint
                GettingStartedStep.APP_LOCK -> R.string.getting_started_lock to R.string.getting_started_lock_hint
                GettingStartedStep.BACKUP -> R.string.getting_started_backup to R.string.getting_started_backup_hint
            }
            val status = stringResource(if (done) R.string.getting_started_done else R.string.getting_started_todo)
            ListItem(
                leadingContent = {
                    Icon(
                        painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked),
                        contentDescription = null,
                        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = {
                    Text(
                        stringResource(title),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                },
                supportingContent = if (done) null else ({ Text(stringResource(hint)) }),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .semantics { stateDescription = status }
                    .then(if (done) Modifier else Modifier.clickable { onStep(step) }),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GettingStartedCardPreview() {
    WealthTheme {
        GettingStartedCard(
            state = GettingStartedUiState(visible = true, done = setOf(GettingStartedStep.ADD_ACCOUNT)),
            onStep = {}, onHide = {},
        )
    }
}
