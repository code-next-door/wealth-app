package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.Routes
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (route: String) -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            SettingsItem(
                R.string.settings_currencies_title,
                R.string.settings_currencies_summary,
            ) { onNavigate(Routes.CURRENCIES) }
            SettingsItem(
                R.string.settings_account_types_title,
                R.string.settings_account_types_summary,
            ) { onNavigate(Routes.ACCOUNT_TYPES) }
            SettingsItem(
                R.string.settings_categories_title,
                R.string.settings_categories_summary,
            ) { onNavigate(Routes.CATEGORIES) }
            SettingsItem(
                R.string.settings_countries_title,
                R.string.settings_countries_summary,
            ) { onNavigate(Routes.COUNTRIES) }
        }
    }
}

@Composable
private fun SettingsItem(title: Int, summary: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WealthTheme { SettingsScreen(onBack = {}, onNavigate = {}) }
}
