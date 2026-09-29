package io.github.codenextdoor.wealth.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseStateTest {

    @Test
    fun onlyKeyProblemsCountAsUnreadable() {
        assertEquals(DatabaseState.Reason.KEY_MISSING, DatabaseState.reasonFor(DatabaseKeyManager.MissingKey()))
        assertEquals(DatabaseState.Reason.KEY_UNAVAILABLE, DatabaseState.reasonFor(DatabaseKeyManager.KeyUnavailable(Exception("keystore"))))
        // SQLCipher with the wrong key, possibly wrapped by Room.
        val wrongKey = RuntimeException("open failed", Exception("file is not a database (code 26)"))
        assertEquals(DatabaseState.Reason.WRONG_KEY, DatabaseState.reasonFor(wrongKey))
    }

    @Test
    fun otherErrorsAreNotTreatedAsALostKey() {
        // E.g. a migration bug: must crash as before, never offer to set the data aside.
        assertNull(DatabaseState.reasonFor(IllegalStateException("Migration didn't properly handle: accounts")))
        assertNull(DatabaseState.reasonFor(RuntimeException("disk I/O error")))
    }
}
