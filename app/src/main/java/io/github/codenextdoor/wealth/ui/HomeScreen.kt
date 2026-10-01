package io.github.codenextdoor.wealth.ui

import io.github.codenextdoor.wealth.ui.tour.tourTarget
import io.github.codenextdoor.wealth.ui.tour.TourTarget
import io.github.codenextdoor.wealth.ui.tour.TourSteps
import io.github.codenextdoor.wealth.ui.tour.TourOverlay
import io.github.codenextdoor.wealth.ui.tour.LocalTourTargets
import io.github.codenextdoor.wealth.ui.tour.LocalTour
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.res.stringResource
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.accounts.AccountsTab
import io.github.codenextdoor.wealth.dashboard.DashboardTab
import io.github.codenextdoor.wealth.expenses.ExpensesTab
import io.github.codenextdoor.wealth.house.HouseTab

enum class HomeTab(val label: Int, @DrawableRes val icon: Int) {
    OVERVIEW(R.string.tab_overview, R.drawable.ic_dashboard),
    ACCOUNTS(R.string.tab_accounts, R.drawable.ic_account_balance_wallet),
    HOUSE(R.string.tab_house, R.drawable.ic_home),
    SPENDING(R.string.tab_spending, R.drawable.ic_receipt_long),
}

/** Main screen: top bar, bottom tabs, and the selected tab's content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    /** Null: any type (e.g. the Overview's first account). */
    onAddAccount: (AssetKind?) -> Unit,
    onOpenAccount: (id: Long) -> Unit,
    /** Opens a stock grant, or a new one for null. */
    onOpenGrant: (id: Long?) -> Unit,
    onOpenBackfill: () -> Unit,
    /** Opens a house by its account, or a new one for null. */
    onOpenHouse: (accountId: Long?) -> Unit,
    onOpenHistory: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (id: Long) -> Unit,
    onImportStatement: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.OVERVIEW) }

    // The tour shows each stop's tab, and ends back on the Overview.
    val tour = LocalTour.current
    val tourStep = tour?.step?.collectAsStateWithLifecycle()?.value
    var touring by remember { mutableStateOf(false) }
    LaunchedEffect(tourStep) {
        if (tourStep != null) {
            touring = true
            TourSteps.all[tourStep].tab?.let { tab = it }
        } else if (touring) {
            touring = false
            tab = HomeTab.OVERVIEW
        }
    }
    val targets = remember { mutableStateMapOf<TourTarget, Rect>() }

    CompositionLocalProvider(LocalTourTargets provides targets) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // While the tour shows, screen readers only get the bubble (like the lock screen).
                modifier = if (tourStep != null) Modifier.clearAndSetSemantics {} else Modifier,
                snackbarHost = { SnackbarHost(LocalAppMessages.current.hostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (tab == HomeTab.OVERVIEW) R.string.app_name else tab.label)) },
                        actions = {
                            if (tab == HomeTab.ACCOUNTS) {
                                IconButton(onClick = onOpenHistory) {
                                    Icon(painterResource(R.drawable.ic_history), stringResource(R.string.history_title))
                                }
                            }
                            // Statements are the main way in; a single expense is the secondary action.
                            if (tab == HomeTab.SPENDING) {
                                IconButton(onClick = onAddExpense) {
                                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.expense_add))
                                }
                            }
                            // Open eye: figures shown; closed eye: shown as "••••" on every tab.
                            val figures = LocalFigures.current
                            IconButton(onClick = figures.toggle, modifier = Modifier.tourTarget(TourTarget.EYE)) {
                                Icon(
                                    painterResource(if (figures.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                    stringResource(if (figures.hidden) R.string.figures_show else R.string.figures_hide),
                                )
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.tourTarget(TourTarget.SETTINGS)) {
                                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        HomeTab.entries.forEach { item ->
                            NavigationBarItem(
                                modifier = when (item) {
                                    HomeTab.OVERVIEW -> Modifier.tourTarget(TourTarget.OVERVIEW_TAB)
                                    HomeTab.HOUSE -> Modifier.tourTarget(TourTarget.HOUSE_TAB)
                                    else -> Modifier
                                },
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    // Extended FABs hide their text from screen readers; the icon's description is the label.
                    when (tab) {
                        // Accounts: each section has its own "+" (assets, liabilities, stock grants).
                        HomeTab.ACCOUNTS -> Unit
                        HomeTab.SPENDING -> ExtendedFloatingActionButton(
                            onClick = onImportStatement,
                            modifier = Modifier.tourTarget(TourTarget.IMPORT),
                            icon = { Icon(painterResource(R.drawable.ic_upload_file), contentDescription = stringResource(R.string.import_title)) },
                            text = { Text(stringResource(R.string.import_title)) },
                        )
                        HomeTab.HOUSE -> ExtendedFloatingActionButton(
                            onClick = { onOpenHouse(null) },
                            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.house_add)) },
                            text = { Text(stringResource(R.string.house_add)) },
                        )
                        HomeTab.OVERVIEW -> Unit
                    }
                },
            ) { padding ->
                when (tab) {
                    HomeTab.OVERVIEW -> DashboardTab(
                        contentPadding = padding,
                        onAddAccount = { onAddAccount(null) },
                        onOpenAccount = onOpenAccount,
                        onOpenHistory = onOpenHistory,
                        onOpenBackfill = onOpenBackfill,
                        onOpenSettings = onOpenSettings,
                    )
                    HomeTab.ACCOUNTS -> AccountsTab(
                        contentPadding = padding,
                        onOpenAccount = onOpenAccount,
                        onOpenGrant = onOpenGrant,
                        onAddAccount = { onAddAccount(it) },
                    )
                    HomeTab.HOUSE -> HouseTab(contentPadding = padding, onOpenHouse = { onOpenHouse(it) })
                    HomeTab.SPENDING -> ExpensesTab(contentPadding = padding, onOpenExpense = onOpenExpense)
                }
            }
            if (tour != null) TourOverlay(tour, targets)
        }
    }
}
