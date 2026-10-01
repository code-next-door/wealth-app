package io.github.codenextdoor.wealth

import kotlinx.coroutines.flow.first
import io.github.codenextdoor.wealth.data.db.DatabaseState
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
        // Keep "today" current while the app stays in memory: at midnight, and on return.
        container.applicationScope.launch { container.today.keepUpToDate() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.today.check()
        })
        // Open the encrypted database first. If its key is gone, the app shows the
        // recovery screen, and the work below waits until recovery made a database.
        container.applicationScope.launch { container.openDatabase() }
        container.applicationScope.launch(Dispatchers.IO) {
            container.databaseState.first { it == DatabaseState.Ready }
            // A brand-new database means a fresh install: only then the first-run help.
            container.onboarding.settle(freshInstall = container.databaseSeeder.seedIfNeeded())
            // At start, then again on each new day.
            container.today.date.collect {
                // Today's rates, and any missing for past balances. Quietly: offline just means next time.
                container.rateUpdater.refresh()
                container.priceUpdater.refresh()
                // A newer release on GitHub? At most once a day; offered in a dialog.
                container.updateChecker.checkIfDue()
            }
        }
    }
}
