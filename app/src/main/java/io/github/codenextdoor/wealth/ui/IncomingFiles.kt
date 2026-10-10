package io.github.codenextdoor.wealth.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Statements opened or shared from another app (e.g. a PDF in Gmail, files in Files):
 * one file goes to Import, several to "Build history from statements". The manifest
 * lists which file types offer Wealth (PDF, CSV, Excel).
 */
object IncomingFiles {

    /** Set on the monthly review notification: the month ("2026-09") to open. */
    const val EXTRA_REVIEW_MONTH = "io.github.codenextdoor.wealth.REVIEW_MONTH"

    /** The screen to open for [intent] (a file, or the monthly review notification), or null. */
    fun route(intent: Intent?): Route? {
        intent?.getStringExtra(EXTRA_REVIEW_MONTH)?.let { return Routes.MonthlyReview(it) }
        val files = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.map { it.toString() }.distinct()
        return when (files.size) {
            0 -> null
            1 -> Routes.ImportFile(files.single())
            else -> Routes.BackfillFiles(files)
        }
    }
}
