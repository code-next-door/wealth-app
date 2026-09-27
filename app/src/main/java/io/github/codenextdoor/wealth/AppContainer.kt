package io.github.codenextdoor.wealth

import android.content.Context
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import io.github.codenextdoor.wealth.data.preferences.AppearancePreferences
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.rates.CurrencyApiSource
import io.github.codenextdoor.wealth.data.rates.FallbackRateSource
import io.github.codenextdoor.wealth.data.rates.FrankfurterSource
import io.github.codenextdoor.wealth.data.rates.HttpsGet
import io.github.codenextdoor.wealth.data.rates.RateSource
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import io.github.codenextdoor.wealth.imports.StatementFileReader
import io.github.codenextdoor.wealth.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: creates and holds single instances of the
 * database and repositories. `by lazy` means each is created on first use.
 */
class AppContainer(
    context: Context,
    /** On-device tests use an in-memory database and their own settings files. */
    private val forTests: Boolean = false,
    /** Where exchange rates are downloaded from; tests pass one that never goes online. */
    private val rateSource: RateSource = FallbackRateSource(FrankfurterSource(HttpsGet), CurrencyApiSource(HttpsGet)),
) {

    private val appContext = context.applicationContext
    private val prefsSuffix = if (forTests) "_test" else ""

    /** For work that must outlive any single screen, e.g. first-run seeding. */
    val applicationScope = CoroutineScope(SupervisorJob())

    val database: WealthDatabase by lazy {
        if (forTests) {
            WealthDatabase.createInMemory(appContext)
        } else {
            WealthDatabase.create(appContext, DatabaseKeyManager(appContext).getOrCreatePassphrase())
        }
    }

    val databaseSeeder by lazy { DatabaseSeeder(database, appContext) }

    val currencyRepository by lazy { CurrencyRepository(database) }

    val catalogRepository by lazy { CatalogRepository(database) }

    val accountRepository by lazy { AccountRepository(database) }

    val expenseRepository by lazy { ExpenseRepository(database) }

    val rateUpdater by lazy { RateUpdater(currencyRepository, accountRepository, rateSource) }

    val statementFileReader by lazy { StatementFileReader(appContext) }

    val appLock by lazy { AppLock(appContext, prefsName = "app_lock$prefsSuffix") }

    val backupRepository by lazy { BackupRepository(database, appContext) }

    val appearancePreferences by lazy { AppearancePreferences(appContext, prefsName = "appearance$prefsSuffix") }
}
