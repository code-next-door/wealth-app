package io.github.codenextdoor.wealth.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes text (UTF-8) to a file the user picked through the system file picker. */
class FileWriter(private val context: Context) {
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("can't open the file")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
