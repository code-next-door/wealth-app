package io.github.codenextdoor.wealth.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.domain.BalanceReminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The weekly check: if balances are out of date, one notification saying how many
 * (never which accounts or amounts: it can show on the lock screen). Tapping it opens
 * the app, where the Overview card lists them.
 */
class BalanceReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WealthApplication).container
        val prefs = container.reminderPreferences
        if (!prefs.enabled.value || !canNotify(applicationContext)) return Result.success()
        // The app's start opens the database; wait for it (a key problem: skip this week).
        withTimeoutOrNull(60_000) { container.databaseState.first { it != DatabaseState.Opening } }
            ?.takeIf { it == DatabaseState.Ready } ?: return Result.success()
        val stale = BalanceReminders.stale(
            accounts = container.accountRepository.accounts.first(),
            entries = container.accountRepository.balanceEntries.first(),
            calculated = container.calculatedAccounts.first(),
            today = container.today.date.value,
            days = prefs.days.value,
        )
        if (stale.isNotEmpty()) notify(applicationContext, stale.size, prefs.days.value)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "balance_reminders"
        private const val NOTIFICATION_ID = 1

        fun canNotify(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

        private fun notify(context: Context, count: Int, days: Int) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.reminders_channel))
                    .build(),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = context.resources.getQuantityString(R.plurals.reminders_notification, count, count, days)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.reminders_notification_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (canNotify(context)) {
                @Suppress("MissingPermission") // checked just above
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }
}
