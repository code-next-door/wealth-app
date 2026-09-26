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
        container.applicationScope.launch(Dispatchers.IO) {
            container.databaseSeeder.seedIfNeeded()
        }
    }
}
