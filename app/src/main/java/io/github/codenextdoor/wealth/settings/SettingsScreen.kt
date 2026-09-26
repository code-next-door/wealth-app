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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import io.github.codenextdoor.wealth.security.AppLock
import io.github.codenextdoor.wealth.security.Biometrics
import io.github.codenextdoor.wealth.security.LockDelay
import io.github.codenextdoor.wealth.security.PinDialog
import io.github.codenextdoor.wealth.security.PinDialogMode
import io.github.codenextdoor.wealth.ui.Routes
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.SectionHeader
import io.github.codenextdoor.wealth.ui.theme.WealthTheme
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val appearance: AppearancePreferences,
    private val appLock: AppLock,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = appearance.themeMode
    val useWallpaperColors: StateFlow<Boolean> = appearance.useWallpaperColors
    val lockSettings: StateFlow<AppLock.LockSettings> = appLock.settings

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)
    fun setUseWallpaperColors(enabled: Boolean) = appearance.setUseWallpaperColors(enabled)

    fun setPin(pin: String) = appLock.setPin(pin)
    fun verifyPin(pin: String) = appLock.verifyPin(pin)
    fun disableLock() = appLock.disable()
    fun setBiometric(enabled: Boolean) = appLock.setBiometricEnabled(enabled)
    fun setDelay(delay: LockDelay) = appLock.setDelay(delay)
    fun setHideInRecents(hide: Boolean) = appLock.setHideInRecents(hide)

    companion object {
        val Factory = appViewModelFactory { SettingsViewModel(it.appearancePreferences, it.appLock) }
    }
}

/** Security-related events from the settings screen. */
data class SecurityActions(
    val onSetPin: (String) -> Unit,
    val onVerifyPin: (String) -> Boolean,
    val onDisable: () -> Unit,
    val onBiometric: (Boolean) -> Unit,
    val onDelay: (LockDelay) -> Unit,
    val onHideInRecents: (Boolean) -> Unit,
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useWallpaperColors by viewModel.useWallpaperColors.collectAsStateWithLifecycle()
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        themeMode = themeMode,
        useWallpaperColors = useWallpaperColors,
        lockSettings = lockSettings,
        biometricAvailable = Biometrics.isAvailable(context),
        security = SecurityActions(
            onSetPin = viewModel::setPin,
            onVerifyPin = viewModel::verifyPin,
            onDisable = viewModel::disableLock,
            onBiometric = viewModel::setBiometric,
            onDelay = viewModel::setDelay,
            onHideInRecents = viewModel::setHideInRecents,
        ),
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeModeChange = viewModel::setThemeMode,
        onUseWallpaperColorsChange = viewModel::setUseWallpaperColors,
        backupSection = { BackupSection() },
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    useWallpaperColors: Boolean,
    lockSettings: AppLock.LockSettings,
    biometricAvailable: Boolean,
    security: SecurityActions,
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUseWallpaperColorsChange: (Boolean) -> Unit,
    backupSection: @Composable () -> Unit = {},
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

            SectionHeader(stringResource(R.string.settings_section_security))
            SecurityCard(lockSettings, biometricAvailable, security)

            SectionHeader(stringResource(R.string.settings_section_backup))
            SettingsCard { backupSection() }

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
                    icon = Icons.Default.Search,
                    title = R.string.settings_rules_title,
                    summary = R.string.settings_rules_summary,
                ) { onNavigate(Routes.RULES) }
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
private fun SecurityCard(settings: AppLock.LockSettings, biometricAvailable: Boolean, actions: SecurityActions) {
    // Which PIN dialog is open: set up / change, or confirm before turning off.
    var creatingPin by rememberSaveable { mutableStateOf(false) }
    var confirmingToDisable by rememberSaveable { mutableStateOf(false) }
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    SettingsCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.security_app_lock)) },
            supportingContent = { Text(stringResource(R.string.security_app_lock_summary)) },
            trailingContent = {
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { on -> if (on) creatingPin = true else confirmingToDisable = true },
                )
            },
            colors = itemColors,
        )
        if (settings.enabled) {
            if (biometricAvailable) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.security_biometric)) },
                    trailingContent = { Switch(checked = settings.biometricEnabled, onCheckedChange = actions.onBiometric) },
                    colors = itemColors,
                    modifier = Modifier.clickable { actions.onBiometric(!settings.biometricEnabled) },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.security_lock_after), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    LockDelay.entries.forEachIndexed { index, delay ->
                        SegmentedButton(
                            selected = settings.delay == delay,
                            onClick = { actions.onDelay(delay) },
                            shape = SegmentedButtonDefaults.itemShape(index, LockDelay.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (delay) {
                                        LockDelay.IMMEDIATELY -> R.string.security_lock_immediately
                                        LockDelay.ONE_MINUTE -> R.string.security_lock_1_min
                                        LockDelay.FIVE_MINUTES -> R.string.security_lock_5_min
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.security_change_pin)) },
                colors = itemColors,
                modifier = Modifier.clickable { creatingPin = true },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.security_hide_recents)) },
            supportingContent = { Text(stringResource(R.string.security_hide_recents_summary)) },
            trailingContent = { Switch(checked = settings.hideInRecents, onCheckedChange = actions.onHideInRecents) },
            colors = itemColors,
            modifier = Modifier.clickable { actions.onHideInRecents(!settings.hideInRecents) },
        )
    }

    if (creatingPin) {
        PinDialog(
            mode = PinDialogMode.CREATE,
            onDone = { pin -> actions.onSetPin(pin); creatingPin = false; true },
            onDismiss = { creatingPin = false },
        )
    }
    if (confirmingToDisable) {
        PinDialog(
            mode = PinDialogMode.CONFIRM_CURRENT,
            onDone = { pin ->
                actions.onVerifyPin(pin).also { ok -> if (ok) { actions.onDisable(); confirmingToDisable = false } }
            },
            onDismiss = { confirmingToDisable = false },
        )
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
        SettingsScreen(
            themeMode = ThemeMode.SYSTEM,
            useWallpaperColors = false,
            lockSettings = AppLock.LockSettings(enabled = true, biometricEnabled = true, delay = LockDelay.ONE_MINUTE, hideInRecents = true),
            biometricAvailable = true,
            security = SecurityActions({}, { true }, {}, {}, {}, {}),
            onBack = {},
            onNavigate = {},
            onThemeModeChange = {},
            onUseWallpaperColorsChange = {},
        )
    }
}
