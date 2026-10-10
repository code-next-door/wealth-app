package io.github.codenextdoor.wealth.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Runs [BalanceReminderWorker] once a week while reminders are on. */
interface ReminderScheduler {
    fun schedule()
    fun cancel()

    /** Device tests: nothing runs in the background (no notifications mid-test). */
    object None : ReminderScheduler {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }
}

class WorkManagerReminders(private val context: Context) : ReminderScheduler {
    // Keeps an existing schedule, so app starts don't push the next check back.
    override fun schedule() {
        val weekly = PeriodicWorkRequestBuilder<BalanceReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, weekly)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "balance-reminders"
    }
}
