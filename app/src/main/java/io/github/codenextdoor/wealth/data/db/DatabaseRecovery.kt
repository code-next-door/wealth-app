package io.github.codenextdoor.wealth.data.db

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Moves a database that can't be opened out of the way, so a new one can be made
 * (from a backup, or empty). Nothing is deleted: the files and their old key go to
 * `files/unreadable-databases/<time>/`, in case the key ever comes back.
 */
class DatabaseRecovery(
    private val context: Context,
    private val keys: DatabaseKeyManager,
    /** The database's file name (tests use their own, never the app's). */
    private val fileName: String = WealthDatabase.FILE_NAME,
) {

    fun setAside(now: LocalDateTime = LocalDateTime.now()): File {
        val folder = File(context.filesDir, "unreadable-databases/${now.format(STAMP)}").apply { mkdirs() }
        val name = fileName
        listOf(name, "$name-wal", "$name-shm", "$name-journal").forEach { file ->
            val from = context.getDatabasePath(file)
            if (from.exists()) check(from.renameTo(File(folder, file))) { "Could not move $file aside" }
        }
        keys.setAsideKey()?.let { File(folder, "wrapped-key.txt").writeText(it) }
        return folder
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
