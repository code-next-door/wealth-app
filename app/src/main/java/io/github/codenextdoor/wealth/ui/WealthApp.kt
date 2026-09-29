package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.onboarding.WelcomeViewModel
import io.github.codenextdoor.wealth.onboarding.WelcomeRoute
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.codenextdoor.wealth.accounts.AccountEditRoute
import io.github.codenextdoor.wealth.backfill.BackfillRoute
import io.github.codenextdoor.wealth.grants.GrantEditRoute
import io.github.codenextdoor.wealth.house.HouseEditRoute
import io.github.codenextdoor.wealth.recurring.RecurringEditRoute
import io.github.codenextdoor.wealth.recurring.RecurringListRoute
import io.github.codenextdoor.wealth.accounts.HistoryRoute
import io.github.codenextdoor.wealth.expenses.ExpenseEditRoute
import io.github.codenextdoor.wealth.expenses.RulesRoute
import io.github.codenextdoor.wealth.imports.ImportRoute
import io.github.codenextdoor.wealth.settings.AccountTypesRoute
import io.github.codenextdoor.wealth.settings.CategoriesRoute
import io.github.codenextdoor.wealth.settings.CountriesRoute
import io.github.codenextdoor.wealth.settings.CurrenciesRoute
import io.github.codenextdoor.wealth.settings.SettingsRoute

/** Root composable: maps each route (screen address) to its screen. */
@Composable
fun WealthApp(navController: NavHostController = rememberNavController()) {
    val scope = rememberCoroutineScope()
    val messages = remember { AppMessages(SnackbarHostState(), scope) }
    val welcome: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory)
    val settled by welcome.settled.collectAsStateWithLifecycle()
    val showWelcome by welcome.visible.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppMessages provides messages) {
        when {
            // Only on the very first start, while the new database is set up.
            !settled -> Surface(Modifier.fillMaxSize()) {}
            // Fresh install only: the welcome screen first, then the app.
            showWelcome -> WelcomeRoute(welcome)
            else -> WealthNavHost(navController)
        }
    }
}

@Composable
private fun WealthNavHost(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable<Routes.Home> {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onAddAccount = { navController.navigate(Routes.AccountEdit()) },
                onOpenAccount = { navController.navigate(Routes.AccountEdit(it)) },
                onOpenGrant = { navController.navigate(Routes.GrantEdit(it)) },
                onOpenRecurring = { navController.navigate(Routes.Recurring) },
                onOpenBackfill = { navController.navigate(Routes.Backfill) },
                onOpenHouse = { navController.navigate(Routes.HouseEdit(it)) },
                onOpenHistory = { navController.navigate(Routes.History) },
                onAddExpense = { navController.navigate(Routes.ExpenseEdit()) },
                onOpenExpense = { navController.navigate(Routes.ExpenseEdit(it)) },
                onImportStatement = { navController.navigate(Routes.Import) },
            )
        }
        composable<Routes.ExpenseEdit> { ExpenseEditRoute(onDone = back) }
        composable<Routes.Rules> { RulesRoute(onBack = back) }
        composable<Routes.Import> { ImportRoute(onDone = back) }
        composable<Routes.History> { HistoryRoute(onBack = back) }
        composable<Routes.AccountEdit> { AccountEditRoute(onDone = back) }
        composable<Routes.GrantEdit> { GrantEditRoute(onDone = back) }
        composable<Routes.HouseEdit> { HouseEditRoute(onDone = back) }
        composable<Routes.Backfill> { BackfillRoute(onDone = back, onAddAccount = { navController.navigate(Routes.AccountEdit()) }) }
        composable<Routes.Recurring> { RecurringListRoute(onBack = back, onOpen = { navController.navigate(Routes.RecurringEdit(it)) }) }
        composable<Routes.RecurringEdit> { RecurringEditRoute(onDone = back) }
        composable<Routes.Settings> {
            SettingsRoute(onBack = back, onNavigate = { navController.navigate(it) })
        }
        composable<Routes.Currencies> { CurrenciesRoute(onBack = back) }
        composable<Routes.AccountTypes> { AccountTypesRoute(onBack = back) }
        composable<Routes.Categories> { CategoriesRoute(onBack = back) }
        composable<Routes.Countries> { CountriesRoute(onBack = back) }
    }
}
