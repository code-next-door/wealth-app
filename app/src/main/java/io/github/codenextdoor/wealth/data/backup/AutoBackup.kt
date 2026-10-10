package io.github.codenextdoor.wealth.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.codenextdoor.wealth.data.preferences.AutoBackupPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** The chosen backup file: written through Android's file access, kept usable across restarts. */
interface BackupFiles {
    suspend fun write(uri: Uri, bytes: ByteArray)

    /** Keeps the right to write [uri] after the app restarts. */
    fun keep(uri: Uri)

    fun release(uri: Uri)
}

class AndroidBackupFiles(private val context: Context) : BackupFiles {
    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // The whole file is encrypted in memory first, so it's replaced in one write.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("can't open the backup file")
        stream.use { it.write(bytes) }
    }

    // Not every file can be kept (e.g. a plain file in tests): then it works until the app restarts.
    override fun keep(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, FLAGS) }
    }

    override fun release(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, FLAGS) }
    }

    private companion object {
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/**
 * Automatic backups: the same encrypted file as "Export encrypted backup", written over
 * one file the user picked, at most daily (the worker) and only when the data changed.
 */
class AutoBackup(
    private val repository: BackupRepository,
    private val prefs: AutoBackupPreferences,
    private val files: BackupFiles,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Key-derivation rounds; fewer only in tests. */
    private val iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    /** After each backup made (ticks "Make a backup" on the getting-started list). */
    private val onBackedUp: () -> Unit = {},
) {
    enum class Outcome { OFF, UNCHANGED, DONE, FAILED_NEW, FAILED_AGAIN }

    // The daily worker and "Back up now" never write the file at the same time.
    private val running = Mutex()

    fun turnOn(file: Uri, password: CharArray) {
        files.keep(file)
        prefs.turnOn(file, password)
    }

    fun turnOff() {
        prefs.state.value.file?.let(files::release)
        prefs.turnOff()
    }

    fun changeFile(file: Uri) {
        val old = prefs.state.value.file
        files.keep(file)
        if (old != null && old != file) files.release(old)
        prefs.setFile(file)
    }

    /** One backup if due; [force] = even when nothing changed ("Back up now"). */
    suspend fun run(force: Boolean = false): Outcome = running.withLock {
        val state = prefs.state.value
        val file = state.file
        if (!state.on || file == null) return@withLock Outcome.OFF
        try {
            val password = prefs.password() ?: error("no password")
            val snapshot = repository.snapshot()
            // The time of the snapshot isn't a change.
            val hash = sha256(snapshot.copy(createdAt = 0).toJson())
            // After a failure the file may be broken or gone: write it again even if nothing changed.
            if (!force && state.failedAt == null && hash == prefs.lastHash) return@withLock Outcome.UNCHANGED
            val bytes = withContext(Dispatchers.Default) {
                try {
                    BackupCrypto.encrypt(snapshot.toJson().toByteArray(), password, iterations)
                } finally {
                    password.fill(' ')
                }
            }
            files.write(file, bytes)
            prefs.recordSuccess(clock(), hash)
            onBackedUp()
            Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.recordFailure(clock())
            if (state.failedAt == null) Outcome.FAILED_NEW else Outcome.FAILED_AGAIN
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
