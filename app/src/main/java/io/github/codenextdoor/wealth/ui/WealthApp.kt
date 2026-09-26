package io.github.codenextdoor.wealth.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.codenextdoor.wealth.accounts.AccountEditRoute
import io.github.codenextdoor.wealth.accounts.AccountEditViewModel
import io.github.codenextdoor.wealth.settings.AccountTypesRoute
import io.github.codenextdoor.wealth.settings.CategoriesRoute
import io.github.codenextdoor.wealth.settings.CountriesRoute
import io.github.codenextdoor.wealth.settings.CurrenciesRoute
import io.github.codenextdoor.wealth.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CURRENCIES = "settings/currencies"
    const val ACCOUNT_TYPES = "settings/account-types"
    const val CATEGORIES = "settings/categories"
    const val COUNTRIES = "settings/countries"
    const val ACCOUNT_EDIT = "accounts/edit?${AccountEditViewModel.ARG_ACCOUNT_ID}={${AccountEditViewModel.ARG_ACCOUNT_ID}}"

    /** Pass no id to add a new account. */
    fun accountEdit(id: Long? = null) =
        if (id == null) "accounts/edit" else "accounts/edit?${AccountEditViewModel.ARG_ACCOUNT_ID}=$id"
}

/** Root composable: maps each route (screen address) to its screen. */
@Composable
fun WealthApp() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddAccount = { navController.navigate(Routes.accountEdit()) },
                onOpenAccount = { navController.navigate(Routes.accountEdit(it)) },
            )
        }
        composable(
            Routes.ACCOUNT_EDIT,
            arguments = listOf(
                navArgument(AccountEditViewModel.ARG_ACCOUNT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { AccountEditRoute(onDone = back) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onNavigate = { navController.navigate(it) })
        }
        composable(Routes.CURRENCIES) { CurrenciesRoute(onBack = back) }
        composable(Routes.ACCOUNT_TYPES) { AccountTypesRoute(onBack = back) }
        composable(Routes.CATEGORIES) { CategoriesRoute(onBack = back) }
        composable(Routes.COUNTRIES) { CountriesRoute(onBack = back) }
    }
}
