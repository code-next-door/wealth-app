package io.github.codenextdoor.wealth.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.AutoBackupPreferences
import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import io.github.codenextdoor.wealth.security.FakeSecretBox
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Automatic backups: one chosen file, rewritten at most daily and only when the data changed. */
@RunWith(AndroidJUnit4::class)
class AutoBackupTest : DatabaseTest() {

    private val box = FakeSecretBox()
    private val prefs by lazy { AutoBackupPreferences(SettingsStore(context, "autobackup_${System.nanoTime()}"), box) }
    private val files = FakeBackupFiles()
    private var now = 1_000_000L
    private val uri = Uri.parse("content://drive/backup-file")

    private fun autoBackup() = AutoBackup(BackupRepository(db, context), prefs, files, clock = { now }, iterations = 1_000)

    private fun backedUp(): BackupSnapshot =
        BackupSnapshot.fromJson(String(BackupCrypto.decrypt(files.written.getValue(uri), "long password".toCharArray())))

    @Test
    fun turningOnKeepsTheFileAndThePasswordSealed() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        assertTrue(prefs.state.value.on)
        assertEquals(uri, prefs.state.value.file)
        assertEquals("long password", String(prefs.password()!!))
        // Not stored as plain text anywhere in the settings file.
        assertFalse(prefs.store.current.asMap().values.any { it.toString().contains("long password") })
        // Survives a restart.
        assertEquals(prefs.state.value, AutoBackupPreferences(prefs.store, box).state.value)
    }

    @Test
    fun writesOnlyWhenTheDataChanged() = runBlocking {
        addAccount("Savings")
        prefs.turnOn(uri, "long password".toCharArray())
        val backup = autoBackup()

        assertEquals(AutoBackup.Outcome.DONE, backup.run())
        assertEquals(listOf("Savings"), backedUp().accounts.map { it.name })
        assertEquals(now, prefs.state.value.lastSuccess)

        now += 86_400_000
        assertEquals(AutoBackup.Outcome.UNCHANGED, backup.run())
        assertEquals(1, files.writes)

        addAccount("Pillar 3a")
        assertEquals(AutoBackup.Outcome.DONE, backup.run())
        assertEquals(2, files.writes)
        assertEquals(setOf("Savings", "Pillar 3a"), backedUp().accounts.map { it.name }.toSet())
        assertEquals(now, prefs.state.value.lastSuccess)
    }

    @Test
    fun backUpNowWritesEvenWithoutChanges() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        val backup = autoBackup()
        backup.run()
        assertEquals(AutoBackup.Outcome.DONE, backup.run(force = true))
        assertEquals(2, files.writes)
    }

    @Test
    fun offWritesNothing() = runBlocking {
        assertEquals(AutoBackup.Outcome.OFF, autoBackup().run())
        prefs.turnOn(uri, "long password".toCharArray())
        prefs.turnOff()
        assertEquals(AutoBackup.Outcome.OFF, autoBackup().run(force = true))
        assertEquals(0, files.writes)
        assertNull(prefs.password()) // forgotten when switched off
    }

    @Test
    fun aFailureIsRecordedAndAnnouncedOnceUntilABackupWorksAgain() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        files.failWith = IOException("Drive access lost")
        val backup = autoBackup()

        assertEquals(AutoBackup.Outcome.FAILED_NEW, backup.run())
        assertEquals(now, prefs.state.value.failedAt)
        now += 86_400_000
        assertEquals(AutoBackup.Outcome.FAILED_AGAIN, backup.run()) // no second notification
        assertEquals(now - 86_400_000, prefs.state.value.failedAt) // since when it fails

        files.failWith = null
        assertEquals(AutoBackup.Outcome.DONE, backup.run())
        assertNull(prefs.state.value.failedAt)
        assertNotNull(prefs.state.value.lastSuccess)
    }

    @Test
    fun aFailedBackupIsRetriedEvenWithoutChanges() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        val backup = autoBackup()
        backup.run()
        files.failWith = IOException("gone")
        assertEquals(AutoBackup.Outcome.FAILED_NEW, backup.run(force = true))
        files.failWith = null
        assertEquals(AutoBackup.Outcome.DONE, backup.run()) // same data, but the file may be broken
    }

    @Test
    fun aLostPasswordKeyIsAFailure() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        box.lost = true
        assertEquals(AutoBackup.Outcome.FAILED_NEW, autoBackup().run())
        assertEquals(0, files.writes)
    }

    @Test
    fun choosingAnotherFileMovesThePermissionAndBacksUpThere() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        files.keep(uri)
        val backup = autoBackup()
        backup.run()
        val other = Uri.parse("content://drive/other-file")
        backup.changeFile(other)
        assertEquals(setOf(other), files.kept)
        assertEquals(other, prefs.state.value.file)
        assertEquals(AutoBackup.Outcome.DONE, backup.run()) // the new file is empty so far
        assertTrue(other in files.written)
    }

    @Test
    fun changingThePasswordWritesWithTheNewOne() = runBlocking {
        prefs.turnOn(uri, "long password".toCharArray())
        val backup = autoBackup()
        backup.run()
        prefs.setPassword("another password".toCharArray())
        assertEquals(AutoBackup.Outcome.DONE, backup.run())
        BackupCrypto.decrypt(files.written.getValue(uri), "another password".toCharArray())
        Unit
    }

    private class FakeBackupFiles : BackupFiles {
        val written = mutableMapOf<Uri, ByteArray>()
        val kept = mutableSetOf<Uri>()
        var writes = 0
        var failWith: Exception? = null

        override suspend fun write(uri: Uri, bytes: ByteArray) {
            failWith?.let { throw it }
            written[uri] = bytes
            writes++
        }

        override fun keep(uri: Uri) {
            kept += uri
        }

        override fun release(uri: Uri) {
            kept -= uri
        }
    }
}
