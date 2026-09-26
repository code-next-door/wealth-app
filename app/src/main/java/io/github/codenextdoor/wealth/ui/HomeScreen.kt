package io.github.codenextdoor.wealth.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.accounts.AccountsTab
import io.github.codenextdoor.wealth.dashboard.DashboardTab

enum class HomeTab(val label: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Default.Home),
    ACCOUNTS(R.string.tab_accounts, Icons.AutoMirrored.Filled.List),
}

/** Main screen: top bar, bottom tabs, and the selected tab's content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (id: Long) -> Unit,
    onOpenHistory: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (tab == HomeTab.OVERVIEW) R.string.app_name else tab.label)) },
                actions = {
                    if (tab == HomeTab.ACCOUNTS) {
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Default.DateRange, stringResource(R.string.history_title))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == HomeTab.ACCOUNTS) {
                ExtendedFloatingActionButton(
                    onClick = onAddAccount,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.account_add)) },
                )
            }
        },
    ) { padding ->
        when (tab) {
            HomeTab.OVERVIEW -> DashboardTab(
                contentPadding = padding,
                onAddAccount = onAddAccount,
                onOpenAccount = onOpenAccount,
                onOpenHistory = onOpenHistory,
            )
            HomeTab.ACCOUNTS -> AccountsTab(contentPadding = padding, onOpenAccount = onOpenAccount)
        }
    }
}
