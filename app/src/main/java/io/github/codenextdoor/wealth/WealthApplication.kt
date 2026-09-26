package io.github.codenextdoor.wealth

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WealthApplication : Application() {

    /** Holds app-wide dependencies (database, repositories). See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Create the lock now so it starts watching app visibility from the first screen.
        container.appLock
        container.applicationScope.launch(Dispatchers.IO) {
            container.databaseSeeder.seedIfNeeded()
        }
    }
}
