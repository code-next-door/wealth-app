package io.github.codenextdoor.wealth

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class WealthApplication : Application() {

    /** Holds app-wide dependencies (database, repositories). See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    /** On-device tests override this to use an isolated, in-memory container. */
    protected open fun createContainer() = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        // Debug builds log (never crash on) slow work on the main thread and leaked
        // resources, e.g. an unclosed file. See logcat tag "StrictMode".
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        container = createContainer()
        // Create the lock now so it starts watching app visibility from the first screen.
        container.appLock
        container.applicationScope.launch(Dispatchers.IO) {
            container.databaseSeeder.seedIfNeeded()
            // Rent, subscriptions etc. that fell due since the app was last opened.
            container.recurringRepository.addDue(java.time.LocalDate.now())
            // Today's rates, and any missing for past balances. Quietly: offline just means next time.
            container.rateUpdater.refresh()
            container.priceUpdater.refresh()
        }
    }
}
