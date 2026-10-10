package io.github.codenextdoor.wealth.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.codenextdoor.wealth.MainActivity
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.WealthApplication
import io.github.codenextdoor.wealth.data.db.DatabaseState
import io.github.codenextdoor.wealth.domain.ReviewNotice
import io.github.codenextdoor.wealth.ui.IncomingFiles
import io.github.codenextdoor.wealth.ui.localDateFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth

/**
 * The daily check: in a month's first week, once, "Your September review is ready".
 * No figures (it can show on the lock screen); tapping it opens that review.
 */
class MonthlyReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WealthApplication).container
        val prefs = container.reminderPreferences
        if (!prefs.reviewEnabled.value || !BalanceReminderWorker.canNotify(applicationContext)) return Result.success()
        withTimeoutOrNull(60_000) { container.databaseState.first { it != DatabaseState.Opening } }
            ?.takeIf { it == DatabaseState.Ready } ?: return Result.success()
        val hasAccounts = container.accountRepository.accounts.first().isNotEmpty()
        val month = ReviewNotice.due(container.today.date.value, prefs.reviewNotified, hasAccounts) ?: return Result.success()
        notify(applicationContext, month)
        prefs.setReviewNotified(month)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "monthly_review"
        private const val NOTIFICATION_ID = 2

        private fun notify(context: Context, month: YearMonth) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.review_title))
                    .build(),
            )
            // A fresh start on the review (behind the app lock as always).
            val open = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(IncomingFiles.EXTRA_REVIEW_MONTH, month.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.review_notification, month.atDay(1).format(localDateFormat("MMMM"))))
                .setContentText(context.getString(R.string.review_notification_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (BalanceReminderWorker.canNotify(context)) {
                @Suppress("MissingPermission") // checked just above
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }
}
