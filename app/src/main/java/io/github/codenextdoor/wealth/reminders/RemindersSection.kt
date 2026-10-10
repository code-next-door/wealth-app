package io.github.codenextdoor.wealth.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.BalanceReminders
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption

/** Settings › Reminders: balance reminders (on/off, after how many days) and the monthly review. */
@Composable
fun RemindersSection(viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(
        headlineContent = { Text(stringResource(R.string.reminders_switch)) },
        supportingContent = { Text(stringResource(R.string.reminders_switch_summary)) },
        trailingContent = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled) },
        colors = colors,
        modifier = Modifier.clickable { viewModel.setEnabled(!state.enabled) },
    )
    if (state.enabled) {
        val options = BalanceReminders.DAY_CHOICES.map { DropdownOption(it, pluralStringResource(R.plurals.reminders_days, it, it)) }
        DropdownField(
            label = stringResource(R.string.reminders_after),
            options = options,
            selected = state.days,
            onSelect = viewModel::setDays,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.review_switch)) },
        supportingContent = { Text(stringResource(R.string.review_switch_summary)) },
        trailingContent = { Switch(checked = state.reviewEnabled, onCheckedChange = viewModel::setReviewEnabled) },
        colors = colors,
        modifier = Modifier.clickable { viewModel.setReviewEnabled(!state.reviewEnabled) },
    )
}
