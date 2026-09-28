package io.github.codenextdoor.wealth.data.backup

import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSnapshotTest {

    private val snapshot = sampleSnapshot

    @Test
    fun roundTripsEverythingIncludingNulls() {
        assertEquals(snapshot, BackupSnapshot.fromJson(snapshot.toJson()))
    }

    @Test(expected = BackupSnapshot.Companion.UnsupportedBackup::class)
    fun rejectsNewerFormat() {
        BackupSnapshot.fromJson(snapshot.toJson().replace("\"format\":${BackupSnapshot.FORMAT_VERSION}", "\"format\":99"))
    }

    @Test
    fun readsVersion1WhereEveryRateWasTyped() {
        val v1 = snapshot.toJson()
            .replace("\"format\":${BackupSnapshot.FORMAT_VERSION}", "\"format\":1")
            .replace(",\"source\":\"fetched\"", "").replace(",\"source\":\"manual\"", "")
        val rates = BackupSnapshot.fromJson(v1).exchangeRates
        assertEquals(listOf(ExchangeRateEntity.MANUAL, ExchangeRateEntity.MANUAL), rates.map { it.source })
    }

    @Test(expected = BackupSnapshot.Companion.UnsupportedBackup::class)
    fun rejectsOtherApps() {
        BackupSnapshot.fromJson("""{"app":"something.else","format":1,"createdAt":0}""")
    }
}
