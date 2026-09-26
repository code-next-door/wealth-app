package io.github.codenextdoor.wealth.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written upgrades, for changes Room can't generate on its own.
 * The CREATE statements are copied from the schema JSON Room exports
 * (app/schemas/...), so the result matches what Room expects exactly.
 */
object Migrations {

    /** v3 adds balance history, starting each account's history with its current balance. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `balance_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                    "`balanceMinor` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_entries_accountId_date` " +
                    "ON `balance_entries` (`accountId`, `date`)",
            )
            // balanceUpdatedAt is epoch millis; integer division gives the epoch day (UTC).
            db.execSQL(
                "INSERT INTO balance_entries (accountId, date, balanceMinor) " +
                    "SELECT id, balanceUpdatedAt / 86400000, balanceMinor FROM accounts",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_2_3)
}
