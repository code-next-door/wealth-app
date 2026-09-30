package io.github.codenextdoor.wealth

import io.github.codenextdoor.wealth.data.backup.BackupSnapshot
import io.github.codenextdoor.wealth.data.backup.BackupFileReader
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.codenextdoor.wealth.data.db.DatabaseRecovery
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.ui.tour.Tour
import io.github.codenextdoor.wealth.data.preferences.OnboardingPreferences
import android.content.Context
import io.github.codenextdoor.wealth.data.backup.BackupRepository
import io.github.codenextdoor.wealth.data.Today
import io.github.codenextdoor.wealth.data.db.DatabaseKeyManager
import io.github.codenextdoor.wealth.data.preferences.AppearancePreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.rates.CurrencyApiSource
import io.github.codenextdoor.wealth.data.rates.FallbackRateSource
import io.github.codenextdoor.wealth.data.rates.FrankfurterSource
import io.github.codenextdoor.wealth.data.rates.HttpsGet
import io.github.codenextdoor.wealth.data.rates.PriceSource
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.YahooPriceSource
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
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
    /** Where share prices are downloaded from; tests pass one that never goes online. */
    private val priceSource: PriceSource = YahooPriceSource(HttpsGet),
) {

    private val appContext = context.applicationContext
    private val prefsSuffix = if (forTests) "_test" else ""

    /** For work that must outlive any single screen, e.g. first-run seeding. */
    val applicationScope = CoroutineScope(SupervisorJob())

    private val _databaseState = MutableStateFlow<DatabaseState>(DatabaseState.Opening)

    /**
     * Opening at start; Ready once the encrypted database opened with its key;
     * Unreadable if the key is gone (the app then shows the recovery screen).
     */
    val databaseState: StateFlow<DatabaseState> = _databaseState.asStateFlow()

    @Volatile
    private var openedDatabase: WealthDatabase? = null
    private val databaseLock = Mutex()

    /** Only used once [databaseState] is Ready: the UI and the start-up work wait for it. */
    val database: WealthDatabase
        get() = openedDatabase
            ?: if (forTests) runBlocking { openDatabase(); openedDatabase!! } else error("The database isn't open (${_databaseState.value})")

    // One store per file for the whole app (DataStore allows no second one), kept across retries.
    private val databaseKeys by lazy {
        DatabaseKeyManager(SettingsStore(appContext, DatabaseKeyManager.STORE_NAME)) {
            appContext.getDatabasePath(WealthDatabase.FILE_NAME).exists()
        }
    }

    /** Opens the encrypted database, checking its key (called at start, and by "Try again"). */
    suspend fun openDatabase(): DatabaseState = databaseLock.withLock {
        withContext(Dispatchers.IO) {
            _databaseState.value = if (openedDatabase != null) {
                DatabaseState.Ready
            } else {
                try {
                    openedDatabase = open()
                    DatabaseState.Ready
                } catch (e: Exception) {
                    // Only a key problem shows the recovery screen; any other error crashes as before.
                    DatabaseState.Unreadable(DatabaseState.reasonFor(e) ?: throw e)
                }
            }
            _databaseState.value
        }
    }

    /**
     * Recovery from an unreadable database: sets it aside (never deleted), opens a new one
     * with a new key, lets [fill] put data in (a backup, or nothing) and only then shows the app.
     */
    suspend fun startOverDatabase(fill: suspend (WealthDatabase) -> Unit) = databaseLock.withLock {
        withContext(Dispatchers.IO) {
            // Device tests share the app's folder with real data, and later tests need the
            // shared test database: they only see the screen change, nothing is replaced.
            if (!forTests) {
                check(openedDatabase == null) { "The database is open; nothing to recover" }
                DatabaseRecovery(appContext, databaseKeys).setAside()
                val fresh = open()
                fill(fresh)
                openedDatabase = fresh
            }
            _databaseState.value = DatabaseState.Ready
        }
    }

    private fun open(): WealthDatabase =
        if (forTests) WealthDatabase.createInMemory(appContext) else WealthDatabase.createChecked(appContext, databaseKeys.getOrCreatePassphrase())

    val backupFileReader by lazy { BackupFileReader(appContext) }

    /** Recovery: a new database holding [snapshot] (checked before anything is moved). */
    suspend fun recoverFromBackup(snapshot: BackupSnapshot) = startOverDatabase { BackupRepository(it, appContext).restore(snapshot) }

    /** Recovery: a new, empty database (seeded with the defaults as on a first start). */
    suspend fun recoverWithEmptyDatabase() = startOverDatabase { }

    /** Device tests of the recovery screen: show it without touching any file. */
    fun showRecoveryForTests(reason: DatabaseState.Reason) {
        check(forTests)
        _databaseState.value = DatabaseState.Unreadable(reason)
    }

    /** Today's date, moved on at midnight (see [Today]). */
    val today = Today()

    val databaseSeeder by lazy { DatabaseSeeder(database, appContext) }

    val currencyRepository by lazy { CurrencyRepository(database) }

    val catalogRepository by lazy { CatalogRepository(database) }

    val accountRepository by lazy { AccountRepository(database) }

    val expenseRepository by lazy { ExpenseRepository(database) }

    val rateUpdater by lazy { RateUpdater(currencyRepository, accountRepository, rateSource) }

    val shareRepository by lazy { ShareRepository(database) }

    val houseRepository by lazy { HouseRepository(database, accountRepository) }

    val priceUpdater by lazy { PriceUpdater(shareRepository, accountRepository, priceSource) }

    val statementFileReader by lazy { StatementFileReader(appContext) }

    val appLock by lazy { AppLock(SettingsStore(appContext, "app_lock$prefsSuffix")) }

    val backupRepository by lazy { BackupRepository(database, appContext) }

    val appearancePreferences by lazy { AppearancePreferences(SettingsStore(appContext, "appearance$prefsSuffix")) }

    /** First-run help: shown only on a fresh install (see [OnboardingPreferences]). */
    val onboarding by lazy { OnboardingPreferences(SettingsStore(appContext, "onboarding$prefsSuffix")) }

    /** The app tour, started from the welcome screen or Settings › Help. */
    val tour = Tour()
}
