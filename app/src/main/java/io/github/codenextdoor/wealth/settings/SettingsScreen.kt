package io.github.codenextdoor.wealth.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.data.preferences.AppearancePreferences
import io.github.codenextdoor.wealth.data.preferences.ThemeMode
import io.github.codenextdoor.wealth.ui.Routes
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.SectionHeader
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val appearance: AppearancePreferences) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = appearance.themeMode
    val useWallpaperColors: StateFlow<Boolean> = appearance.useWallpaperColors

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)
    fun setUseWallpaperColors(enabled: Boolean) = appearance.setUseWallpaperColors(enabled)

    companion object {
        val Factory = appViewModelFactory { SettingsViewModel(it.appearancePreferences) }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useWallpaperColors by viewModel.useWallpaperColors.collectAsStateWithLifecycle()
    SettingsScreen(
        themeMode = themeMode,
        useWallpaperColors = useWallpaperColors,
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeModeChange = viewModel::setThemeMode,
        onUseWallpaperColorsChange = viewModel::setUseWallpaperColors,
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    useWallpaperColors: Boolean,
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUseWallpaperColorsChange: (Boolean) -> Unit,
) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                // Wallpaper-based colors only exist on Android 12 and newer.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors)) },
                        supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_summary)) },
                        trailingContent = { Switch(checked = useWallpaperColors, onCheckedChange = onUseWallpaperColorsChange) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable { onUseWallpaperColorsChange(!useWallpaperColors) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_data))
            SettingsCard {
                SettingsItem(
                    icon = null,
                    symbol = "¤",
                    title = R.string.settings_currencies_title,
                    summary = R.string.settings_currencies_summary,
                ) { onNavigate(Routes.CURRENCIES) }
                SettingsItem(
                    icon = Icons.Default.AccountBox,
                    title = R.string.settings_account_types_title,
                    summary = R.string.settings_account_types_summary,
                ) { onNavigate(Routes.ACCOUNT_TYPES) }
                SettingsItem(
                    icon = Icons.Default.ShoppingCart,
                    title = R.string.settings_categories_title,
                    summary = R.string.settings_categories_summary,
                ) { onNavigate(Routes.CATEGORIES) }
                SettingsItem(
                    icon = Icons.Default.Place,
                    title = R.string.settings_countries_title,
                    summary = R.string.settings_countries_summary,
                ) { onNavigate(Routes.COUNTRIES) }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun SettingsItem(
    icon: ImageVector?,
    title: Int,
    summary: Int,
    symbol: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(symbol.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WealthTheme {
        SettingsScreen(ThemeMode.SYSTEM, false, {}, {}, {}, {})
    }
}
