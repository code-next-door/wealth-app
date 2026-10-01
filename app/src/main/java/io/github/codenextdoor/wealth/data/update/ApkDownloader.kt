package io.github.codenextdoor.wealth.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads a file's bytes; null when that fails. Tests pass one that never goes online. */
fun interface HttpBytes {
    suspend fun get(url: String): ByteArray?
}

/** HTTPS download with timeouts; follows GitHub's redirect to its file storage. Sends nothing but the URL. */
object HttpsBytes : HttpBytes {
    override suspend fun get(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Wealth (Android app)")
                if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/**
 * Fetches a release's APK into [folder] (in the app's cache; looked up off the main
 * thread, since finding it reads the disk), keeping it only if its SHA-256
 * matches the release's checksum file: a damaged download is never offered to install.
 * Android itself then installs it only if it's signed with the same key as this app.
 */
class ApkDownloader(private val folder: () -> File, private val http: HttpBytes = HttpsBytes) {

    suspend fun download(release: Release): File? {
        val apkUrl = release.apkUrl ?: return null
        val shaUrl = release.sha256Url ?: return null
        // One update at a time: earlier downloads go, so a failed one leaves nothing behind.
        val dir = withContext(Dispatchers.IO) { folder().apply { deleteRecursively() } }
        val expected = http.get(shaUrl)?.toString(Charsets.UTF_8)?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase() ?: return null
        val bytes = http.get(apkUrl) ?: return null
        val actual = withContext(Dispatchers.Default) { MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        if (actual != expected) return null
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, apkUrl.substringAfterLast('/')).apply { writeBytes(bytes) }
        }
    }
}
