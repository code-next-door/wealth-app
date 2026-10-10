package io.github.codenextdoor.wealth.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.onboarding.WelcomeViewModel
import io.github.codenextdoor.wealth.onboarding.WelcomeRoute
import io.github.codenextdoor.wealth.update.UpdateDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.codenextdoor.wealth.accounts.AccountEditRoute
import io.github.codenextdoor.wealth.backfill.BackfillRoute
import io.github.codenextdoor.wealth.grants.GrantEditRoute
import io.github.codenextdoor.wealth.house.HouseEditRoute
import io.github.codenextdoor.wealth.accounts.HistoryRoute
import io.github.codenextdoor.wealth.expenses.ExpenseEditRoute
import io.github.codenextdoor.wealth.imports.ImportRoute
import io.github.codenextdoor.wealth.settings.AccountTypesRoute
import io.github.codenextdoor.wealth.settings.CategoriesRoute
import io.github.codenextdoor.wealth.settings.CategoryDetailRoute
import io.github.codenextdoor.wealth.settings.CountriesRoute
import io.github.codenextdoor.wealth.settings.CurrenciesRoute
import io.github.codenextdoor.wealth.settings.SettingsRoute
import io.github.codenextdoor.wealth.expenses.SearchRoute
import io.github.codenextdoor.wealth.regular.RegularPaymentsRoute
import androidx.core.net.toUri
import io.github.codenextdoor.wealth.review.MonthlyReviewRoute
import java.time.YearMonth

/** Root composable: the welcome screen on a fresh install, then the app's screens. */
@Composable
fun WealthApp(incoming: Route? = null) {
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
            else -> {
                WealthNavigation(incoming)
                // A newer release, found by the daily check: offered over any screen.
                UpdateDialog()
            }
        }
    }
}

/**
 * The screens (Navigation 3): the back stack is a list of [Routes], saved across
 * process death; going somewhere adds a key, Back removes the last. Each screen
 * keeps its own saved state and ViewModels while it's on the stack.
 */
@Composable
private fun WealthNavigation(incoming: Route?) {
    val backStack = rememberNavBackStack(Routes.Home)
    // A file from another app opens on top of Home, so Back leads into the app.
    LaunchedEffect(incoming) { if (incoming != null && backStack.lastOrNull() != incoming) backStack.add(incoming) }
    val go: (Route) -> Unit = { backStack.add(it) }
    val back: () -> Unit = { backStack.removeLastOrNull() }
    // The same short cross-fade as before (Navigation 2's default).
    val fade = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) }

    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { fade() },
        popTransitionSpec = { fade() },
        predictivePopTransitionSpec = { fade() },
        entryProvider = entryProvider {
            entry<Routes.Home> {
                HomeScreen(
                    onOpenSettings = { go(Routes.Settings) },
                    onAddAccount = { kind -> go(Routes.AccountEdit(kind = kind)) },
                    onOpenAccount = { go(Routes.AccountEdit(it)) },
                    onOpenGrant = { go(Routes.GrantEdit(it)) },
                    onOpenBackfill = { go(Routes.Backfill) },
                    onOpenHouse = { go(Routes.HouseEdit(it)) },
                    onOpenHistory = { go(Routes.History) },
                    onAddExpense = { go(Routes.ExpenseEdit()) },
                    onOpenExpense = { go(Routes.ExpenseEdit(it)) },
                    onImportStatement = { go(Routes.Import) },
                    onSearch = { go(Routes.Search) },
                    onSearchCategory = { go(Routes.SearchFor(categoryId = it)) },
                    onOpenRegularPayments = { go(Routes.RegularPayments) },
                    onOpenReview = { month -> go(Routes.MonthlyReview(month?.toString())) },
                )
            }
            entry<Routes.ExpenseEdit> { ExpenseEditRoute(it.expenseId, onDone = back, onSearchShop = { shop -> go(Routes.SearchFor(text = shop)) }) }
            entry<Routes.Rules> { CategoriesRoute(onBack = back, onOpen = { go(Routes.CategoryDetail(it)) }) }
            entry<Routes.Import> { ImportRoute(onDone = back) }
            entry<Routes.ImportFile> { ImportRoute(onDone = back, file = it.uri.toUri()) }
            entry<Routes.BackfillFiles> { key ->
                BackfillRoute(onDone = back, onAddAccount = { go(Routes.AccountEdit()) }, files = key.uris.map { it.toUri() })
            }
            entry<Routes.MonthlyReview> { key ->
                MonthlyReviewRoute(key.month?.let(YearMonth::parse), onBack = back, onOpenAccount = { go(Routes.AccountEdit(it)) })
            }
            entry<Routes.Search> { SearchRoute(onBack = back, onOpenExpense = { go(Routes.ExpenseEdit(it)) }) }
            entry<Routes.RegularPayments> { RegularPaymentsRoute(onBack = back, onOpenShop = { go(Routes.SearchFor(text = it)) }) }
            entry<Routes.SearchFor> { key ->
                SearchRoute(onBack = back, onOpenExpense = { go(Routes.ExpenseEdit(it)) }, text = key.text, categoryId = key.categoryId)
            }
            entry<Routes.History> { HistoryRoute(onBack = back) }
            entry<Routes.AccountEdit> { AccountEditRoute(it.accountId, onDone = back, kind = it.kind) }
            entry<Routes.GrantEdit> { GrantEditRoute(it.grantId, onDone = back) }
            entry<Routes.HouseEdit> { HouseEditRoute(it.accountId, onDone = back) }
            entry<Routes.Backfill> { BackfillRoute(onDone = back, onAddAccount = { go(Routes.AccountEdit()) }) }
            // Removed screens: a back stack saved by an older version just returns from them.
            entry<Routes.Recurring> { LaunchedEffect(Unit) { back() } }
            entry<Routes.RecurringEdit> { LaunchedEffect(Unit) { back() } }
            entry<Routes.Settings> { SettingsRoute(onBack = back, onNavigate = go) }
            entry<Routes.Currencies> { CurrenciesRoute(onBack = back) }
            entry<Routes.AccountTypes> { AccountTypesRoute(onBack = back) }
            entry<Routes.Categories> { CategoriesRoute(onBack = back, onOpen = { go(Routes.CategoryDetail(it)) }) }
            entry<Routes.CategoryDetail> { key -> CategoryDetailRoute(key.categoryId, onBack = back) }
            entry<Routes.Countries> { CountriesRoute(onBack = back) }
        },
    )
}

private const val FADE_MS = 700
