package io.github.codenextdoor.wealth

import android.app.Application
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
        container = createContainer()
        // Create the lock now so it starts watching app visibility from the first screen.
        container.appLock
        container.applicationScope.launch(Dispatchers.IO) {
            container.databaseSeeder.seedIfNeeded()
            // Today's rates, and any missing for past balances. Quietly: offline just means next time.
            container.rateUpdater.refresh()
        }
    }
}
