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

    /** v4 turns the single current rate per pair into a dated rate history. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exchange_rate_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromCode` TEXT NOT NULL, " +
                    "`toCode` TEXT NOT NULL, `date` INTEGER NOT NULL, `rate` TEXT NOT NULL, " +
                    "FOREIGN KEY(`fromCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`toCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rate_history_fromCode_toCode_date` " +
                    "ON `exchange_rate_history` (`fromCode`, `toCode`, `date`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_exchange_rate_history_toCode` ON `exchange_rate_history` (`toCode`)",
            )
            // Each existing rate becomes a history entry dated when it was entered.
            db.execSQL(
                "INSERT INTO exchange_rate_history (fromCode, toCode, date, rate) " +
                    "SELECT fromCode, toCode, updatedAt / 86400000, rate FROM exchange_rates",
            )
            db.execSQL("DROP TABLE exchange_rates")
        }
    }

    val ALL = arrayOf(MIGRATION_2_3, MIGRATION_3_4)
}
