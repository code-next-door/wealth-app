package io.github.codenextdoor.wealth.data.update

import io.github.codenextdoor.wealth.data.rates.HttpGet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A published version of the app: its number, release page, and the APK with its checksum file. */
data class Release(val version: String, val pageUrl: String, val apkUrl: String?, val sha256Url: String?)

/** Where the latest release is looked up; tests pass one that never goes online. */
fun interface UpdateSource {
    suspend fun latest(): Release?
}

/**
 * The app's GitHub releases (the only place it's published). One request to GitHub's
 * public API; it sends nothing about the user or their data, only the request itself.
 */
class GitHubReleases(private val http: HttpGet) : UpdateSource {

    override suspend fun latest(): Release? {
        val body = http.get("https://api.github.com/repos/$REPO/releases/latest") ?: return null
        val reply = runCatching { json.decodeFromString<LatestRelease>(body) }.getOrNull() ?: return null
        if (reply.tagName.isBlank() || reply.htmlUrl.isBlank() || reply.draft || reply.prerelease) return null
        val apk = reply.assets.firstOrNull { it.name.endsWith(".apk") }
        val sha = apk?.let { a -> reply.assets.firstOrNull { it.name == a.name + ".sha256" } }
        return Release(reply.tagName.removePrefix("v"), reply.htmlUrl, apk?.url, sha?.url)
    }

    @Serializable
    private class LatestRelease(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private class Asset(val name: String = "", @SerialName("browser_download_url") val url: String = "")

    private companion object {
        const val REPO = "code-next-door/wealth-app"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Release numbers like "0.7.0" (a leading "v" is fine), compared part by part as numbers. */
object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int>? =
        version.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
