package io.github.codenextdoor.wealth.data.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UpdatesTest : DatabaseTest() {

    // An invented reply in the shape of GitHub's "latest release" API.
    private val latestJson = """
        {"tag_name":"v0.7.0","name":"v0.7.0","html_url":"https://github.com/code-next-door/wealth-app/releases/tag/v0.7.0","draft":false,"prerelease":false,
         "assets":[
          {"name":"wealth-0.7.0.apk","browser_download_url":"https://github.com/code-next-door/wealth-app/releases/download/v0.7.0/wealth-0.7.0.apk","size":13018512},
          {"name":"wealth-0.7.0.apk.sha256","browser_download_url":"https://github.com/code-next-door/wealth-app/releases/download/v0.7.0/wealth-0.7.0.apk.sha256","size":82}
         ]}
    """.trimIndent()

    private val release = Release(
        version = "0.7.0",
        pageUrl = "https://github.com/code-next-door/wealth-app/releases/tag/v0.7.0",
        apkUrl = "https://github.com/code-next-door/wealth-app/releases/download/v0.7.0/wealth-0.7.0.apk",
        sha256Url = "https://github.com/code-next-door/wealth-app/releases/download/v0.7.0/wealth-0.7.0.apk.sha256",
    )

    @Test
    fun versionsCompareByTheirNumbers() {
        assertTrue(AppVersion.isNewer("0.7.0", "0.6.2"))
        assertTrue(AppVersion.isNewer("v0.10.0", "0.9.9")) // 10 > 9, not text order
        assertTrue(AppVersion.isNewer("1.0", "0.9.9"))
        assertFalse(AppVersion.isNewer("0.6.2", "0.6.2"))
        assertFalse(AppVersion.isNewer("0.6.1", "0.6.2"))
        assertFalse(AppVersion.isNewer("not a version", "0.6.2"))
    }

    @Test
    fun theLatestGitHubReleaseIsRead() = runBlocking {
        var asked: String? = null
        val source = GitHubReleases { url -> asked = url; latestJson }
        assertEquals(release, source.latest())
        assertEquals("https://api.github.com/repos/code-next-door/wealth-app/releases/latest", asked)
        // Offline, or an unexpected reply: nothing.
        assertNull(GitHubReleases { null }.latest())
        assertNull(GitHubReleases { "{}" }.latest())
    }

    private class FakeSource(var release: Release?) : UpdateSource {
        var calls = 0
        override suspend fun latest(): Release? = release.also { calls++ }
    }

    private fun checker(source: UpdateSource, current: String?, day: () -> LocalDate = { today }, name: String = "updates_${System.nanoTime()}") =
        UpdateChecker(source, UpdatePreferences(SettingsStore(context, name)), current, day)

    @Test
    fun aNewerReleaseIsOfferedOnceADay() = runBlocking {
        val source = FakeSource(release)
        var day = today
        val checker = checker(source, "0.6.2", { day })
        checker.checkIfDue()
        assertEquals(release, checker.available.value)
        checker.dismiss() // "Later"
        assertNull(checker.available.value)
        checker.checkIfDue() // the same day: not asked again
        assertEquals(1, source.calls)
        assertNull(checker.available.value)
        day = day.plusDays(1)
        checker.checkIfDue() // the next day: offered again
        assertEquals(release, checker.available.value)
    }

    @Test
    fun nothingIsOfferedWhenUpToDateOffDevOrOffline() = runBlocking {
        val upToDate = checker(FakeSource(release), "0.7.0")
        upToDate.checkIfDue()
        assertNull(upToDate.available.value)

        // A build without a release version (debug, tests) never asks GitHub.
        val dev = FakeSource(release)
        checker(dev, current = null).checkIfDue()
        assertEquals(0, dev.calls)

        // Switched off in Settings.
        val off = FakeSource(release)
        val switchedOff = checker(off, "0.6.2")
        switchedOff.setEnabled(false)
        switchedOff.checkIfDue()
        assertEquals(0, off.calls)
        assertFalse(switchedOff.enabled.value)

        // Offline: tried again later the same day (only a real answer counts as checked).
        val offline = FakeSource(null)
        val c = checker(offline, "0.6.2")
        c.checkIfDue()
        c.checkIfDue()
        assertEquals(2, offline.calls)
    }

    @Test
    fun theDownloadIsKeptOnlyIfItsChecksumMatches() = runBlocking {
        val apk = "pretend apk bytes".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val dir = File(context.cacheDir, "updates-test").apply { deleteRecursively() }
        try {
            val good = ApkDownloader({ dir }) { url -> if (url.endsWith(".sha256")) "$sha  wealth-0.7.0.apk\n".toByteArray() else apk }
            val file = good.download(release)!!
            assertTrue(file.readBytes().contentEquals(apk))
            assertEquals("wealth-0.7.0.apk", file.name)

            // A damaged or different file: not kept, nothing to install.
            val bad = ApkDownloader({ dir }) { url -> if (url.endsWith(".sha256")) "0".repeat(64).toByteArray() else apk }
            assertNull(bad.download(release))
            assertTrue(dir.listFiles().orEmpty().isEmpty())
            assertNull(ApkDownloader({ dir }) { null }.download(release)) // offline
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun checkNowLooksEvenAfterTodaysCheck() = runBlocking {
        val source = FakeSource(null)
        val checker = checker(source, "0.6.2")
        checker.checkIfDue() // offline
        source.release = release
        assertEquals(release, checker.checkNow())
        assertEquals(release, checker.available.value)
        source.release = release.copy(version = "0.6.2")
        assertNull(checker.checkNow()) // up to date
        assertNull(checker.available.value)
    }

    @Test
    fun theDialogDownloadsChecksAndHidesWhileLocked() = runBlocking {
        val lock = io.github.codenextdoor.wealth.security.AppLock(SettingsStore(context, "lock_update_${System.nanoTime()}"))
        val checker = checker(FakeSource(release), "0.6.2")
        checker.checkIfDue()
        val dir = File(context.cacheDir, "updates-vm-test")
        try {
            val apk = "apk".toByteArray()
            val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
            val vm = io.github.codenextdoor.wealth.update.UpdateViewModel(
                checker,
                ApkDownloader({ dir }) { url -> if (url.endsWith(".sha256")) sha.toByteArray() else apk },
                lock,
            ).cancelledAfterTest()
            assertEquals(release, vm.state.await { it.release != null }.release)
            vm.update()
            val ready = vm.state.await { it.apk != null }
            assertTrue(ready.apk!!.readBytes().contentEquals(apk))
            vm.done()
            assertNull(vm.state.await { it.release == null && it.apk == null }.release)

            // A failed download offers the release page instead.
            checker.checkNow()
            val failing = io.github.codenextdoor.wealth.update.UpdateViewModel(checker, ApkDownloader({ dir }) { null }, lock).cancelledAfterTest()
            failing.state.await { it.release != null }
            failing.update()
            assertTrue(failing.state.await { it.failed }.failed)
        } finally {
            dir.deleteRecursively()
        }
    }
}
