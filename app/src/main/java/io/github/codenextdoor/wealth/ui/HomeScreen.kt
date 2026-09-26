package io.github.codenextdoor.wealth.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import io.github.codenextdoor.wealth.expenses.ExpensesTab

enum class HomeTab(val label: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Default.Home),
    ACCOUNTS(R.string.tab_accounts, Icons.AutoMirrored.Filled.List),
    SPENDING(R.string.tab_spending, Icons.Default.ShoppingCart),
}

/** Main screen: top bar, bottom tabs, and the selected tab's content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (id: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (id: Long) -> Unit,
    onImportStatement: () -> Unit,
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
                    // Statements are the main way in; a single expense is the secondary action.
                    if (tab == HomeTab.SPENDING) {
                        IconButton(onClick = onAddExpense) {
                            Icon(Icons.Default.Add, stringResource(R.string.expense_add))
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
            // Extended FABs hide their text from screen readers; the icon's description is the label.
            when (tab) {
                HomeTab.ACCOUNTS -> ExtendedFloatingActionButton(
                    onClick = onAddAccount,
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.account_add)) },
                    text = { Text(stringResource(R.string.account_add)) },
                )
                HomeTab.SPENDING -> ExtendedFloatingActionButton(
                    onClick = onImportStatement,
                    icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.import_title)) },
                    text = { Text(stringResource(R.string.import_title)) },
                )
                HomeTab.OVERVIEW -> Unit
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
            HomeTab.SPENDING -> ExpensesTab(contentPadding = padding, onOpenExpense = onOpenExpense)
        }
    }
}
