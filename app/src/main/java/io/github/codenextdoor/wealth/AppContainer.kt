package io.github.codenextdoor.wealth

import android.content.Context
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import io.github.codenextdoor.wealth.data.preferences.AppearancePreferences
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: creates and holds single instances of the
 * database and repositories. `by lazy` means each is created on first use.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** For work that must outlive any single screen, e.g. first-run seeding. */
    val applicationScope = CoroutineScope(SupervisorJob())

    val database: WealthDatabase by lazy {
        WealthDatabase.create(appContext, DatabaseKeyManager(appContext).getOrCreatePassphrase())
    }

    val databaseSeeder by lazy { DatabaseSeeder(database, appContext) }

    val currencyRepository by lazy { CurrencyRepository(database) }

    val catalogRepository by lazy { CatalogRepository(database) }

    val accountRepository by lazy { AccountRepository(database) }

    val expenseRepository by lazy { ExpenseRepository(database) }

    val appearancePreferences by lazy { AppearancePreferences(appContext) }
}
