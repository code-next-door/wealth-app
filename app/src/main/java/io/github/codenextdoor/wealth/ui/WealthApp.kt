package io.github.codenextdoor.wealth.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.codenextdoor.wealth.accounts.AccountEditRoute
import io.github.codenextdoor.wealth.grants.GrantEditRoute
import io.github.codenextdoor.wealth.recurring.RecurringEditRoute
import io.github.codenextdoor.wealth.recurring.RecurringEditViewModel
import io.github.codenextdoor.wealth.recurring.RecurringListRoute
import io.github.codenextdoor.wealth.grants.GrantEditViewModel
import io.github.codenextdoor.wealth.accounts.AccountEditViewModel
import io.github.codenextdoor.wealth.accounts.HistoryRoute
import io.github.codenextdoor.wealth.expenses.ExpenseEditRoute
import io.github.codenextdoor.wealth.expenses.ExpenseEditViewModel
import io.github.codenextdoor.wealth.expenses.RulesRoute
import io.github.codenextdoor.wealth.imports.ImportRoute
import io.github.codenextdoor.wealth.settings.AccountTypesRoute
import io.github.codenextdoor.wealth.settings.CategoriesRoute
import io.github.codenextdoor.wealth.settings.CountriesRoute
import io.github.codenextdoor.wealth.settings.CurrenciesRoute
import io.github.codenextdoor.wealth.settings.SettingsRoute

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CURRENCIES = "settings/currencies"
    const val ACCOUNT_TYPES = "settings/account-types"
    const val CATEGORIES = "settings/categories"
    const val COUNTRIES = "settings/countries"
    const val HISTORY = "history"
    const val RULES = "settings/rules"
    const val IMPORT = "import"
    const val EXPENSE_EDIT = "expenses/edit?${ExpenseEditViewModel.ARG_EXPENSE_ID}={${ExpenseEditViewModel.ARG_EXPENSE_ID}}"

    /** Pass no id to add a new expense. */
    fun expenseEdit(id: Long? = null) =
        if (id == null) "expenses/edit" else "expenses/edit?${ExpenseEditViewModel.ARG_EXPENSE_ID}=$id"
    const val ACCOUNT_EDIT = "accounts/edit?${AccountEditViewModel.ARG_ACCOUNT_ID}={${AccountEditViewModel.ARG_ACCOUNT_ID}}"

    const val GRANT_EDIT = "grants/edit?${GrantEditViewModel.ARG_GRANT_ID}={${GrantEditViewModel.ARG_GRANT_ID}}"

    const val RECURRING = "expenses/recurring"
    const val RECURRING_EDIT = "expenses/recurring/edit?${RecurringEditViewModel.ARG_RECURRING_ID}={${RecurringEditViewModel.ARG_RECURRING_ID}}"

    /** Pass no id to add a new recurring expense. */
    fun recurringEdit(id: Long? = null) =
        if (id == null) "expenses/recurring/edit" else "expenses/recurring/edit?${RecurringEditViewModel.ARG_RECURRING_ID}=$id"

    /** Pass no id to add a new stock grant. */
    fun grantEdit(id: Long? = null) =
        if (id == null) "grants/edit" else "grants/edit?${GrantEditViewModel.ARG_GRANT_ID}=$id"

    /** Pass no id to add a new account. */
    fun accountEdit(id: Long? = null) =
        if (id == null) "accounts/edit" else "accounts/edit?${AccountEditViewModel.ARG_ACCOUNT_ID}=$id"
}

/** Root composable: maps each route (screen address) to its screen. */
@Composable
fun WealthApp(navController: NavHostController = rememberNavController()) {
    val scope = rememberCoroutineScope()
    val messages = remember { AppMessages(SnackbarHostState(), scope) }
    CompositionLocalProvider(LocalAppMessages provides messages) {
        WealthNavHost(navController)
    }
}

@Composable
private fun WealthNavHost(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddAccount = { navController.navigate(Routes.accountEdit()) },
                onOpenAccount = { navController.navigate(Routes.accountEdit(it)) },
                onOpenGrant = { navController.navigate(Routes.grantEdit(it)) },
                onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onAddExpense = { navController.navigate(Routes.expenseEdit()) },
                onOpenExpense = { navController.navigate(Routes.expenseEdit(it)) },
                onImportStatement = { navController.navigate(Routes.IMPORT) },
            )
        }
        composable(
            Routes.EXPENSE_EDIT,
            arguments = listOf(
                navArgument(ExpenseEditViewModel.ARG_EXPENSE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { ExpenseEditRoute(onDone = back) }
        composable(Routes.RULES) { RulesRoute(onBack = back) }
        composable(Routes.IMPORT) { ImportRoute(onDone = back) }
        composable(Routes.HISTORY) { HistoryRoute(onBack = back) }
        composable(
            Routes.ACCOUNT_EDIT,
            arguments = listOf(
                navArgument(AccountEditViewModel.ARG_ACCOUNT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { AccountEditRoute(onDone = back) }
        composable(
            Routes.GRANT_EDIT,
            arguments = listOf(
                navArgument(GrantEditViewModel.ARG_GRANT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { GrantEditRoute(onDone = back) }
        composable(Routes.RECURRING) { RecurringListRoute(onBack = back, onOpen = { navController.navigate(Routes.recurringEdit(it)) }) }
        composable(
            Routes.RECURRING_EDIT,
            arguments = listOf(
                navArgument(RecurringEditViewModel.ARG_RECURRING_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { RecurringEditRoute(onDone = back) }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = back, onNavigate = { navController.navigate(it) })
        }
        composable(Routes.CURRENCIES) { CurrenciesRoute(onBack = back) }
        composable(Routes.ACCOUNT_TYPES) { AccountTypesRoute(onBack = back) }
        composable(Routes.CATEGORIES) { CategoriesRoute(onBack = back) }
        composable(Routes.COUNTRIES) { CountriesRoute(onBack = back) }
    }
}
