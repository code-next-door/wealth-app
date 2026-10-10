package io.github.codenextdoor.wealth.data.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.reminders.BalanceReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Runs [AutoBackupWorker] once a day while automatic backups are on. */
interface AutoBackupScheduler {
    fun schedule()
    fun cancel()

    /** Device tests: nothing runs in the background. */
    object None : AutoBackupScheduler {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }
}

class WorkManagerAutoBackup(private val context: Context) : AutoBackupScheduler {
    // Keeps an existing schedule, so app starts don't push the next backup back.
    override fun schedule() {
        val daily = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "auto-backup"
    }
}

/**
 * The daily backup ([AutoBackup.run]). When backups start failing, one notification
 * (no figures or names: it can show on the lock screen); Settings says since when.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WealthApplication).container
        // The app's start opens the database; wait for it (a key problem: skip today).
        withTimeoutOrNull(60_000) { container.databaseState.first { it != DatabaseState.Opening } }
            ?.takeIf { it == DatabaseState.Ready } ?: return Result.success()
        if (container.autoBackup.run() == AutoBackup.Outcome.FAILED_NEW) notifyFailed(applicationContext)
        return Result.success()
    }

    private companion object {
        const val CHANNEL = "auto_backup"
        const val NOTIFICATION_ID = 3

        fun notifyFailed(context: Context) {
            if (!BalanceReminderWorker.canNotify(context)) return
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.auto_backup_channel))
                    .build(),
            )
            val open = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.auto_backup_failed_title))
                .setContentText(context.getString(R.string.auto_backup_failed_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission") // canNotify checked above
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
