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

    /** The screen to open for [intent], or null when it brings no file. */
    fun route(intent: Intent?): Route? {
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
