package io.github.codenextdoor.wealth.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a backup file without needing the database (the recovery screen has none). */
class BackupFileReader(private val context: Context) {

    /** Decrypts and reads a backup. Throws the [BackupCrypto] errors for wrong passwords or other files. */
    suspend fun read(uri: Uri, password: CharArray): BackupSnapshot {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
        return withContext(Dispatchers.Default) { BackupSnapshot.fromJson(String(BackupCrypto.decrypt(bytes, password))) }
    }
}
