package io.github.codenextdoor.wealth.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        CurrencyEntity::class,
        ExchangeRateEntity::class,
        CountryEntity::class,
        AccountTypeEntity::class,
        ExpenseCategoryEntity::class,
        SettingEntity::class,
        AccountEntity::class,
        BalanceEntryEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Upgrades existing installs without losing data. Room generates the SQL
    // by comparing the committed schema files (app/schemas/.../N.json).
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Adds the accounts table.
    ],
)
abstract class WealthDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun countryDao(): CountryDao
    abstract fun accountTypeDao(): AccountTypeDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun accountDao(): AccountDao
    abstract fun balanceEntryDao(): BalanceEntryDao

    companion object {
        private const val FILE_NAME = "wealth.db"

        /**
         * Opens the database file, encrypted with SQLCipher using [passphrase].
         * Schema changes must add a Migration here — never a destructive fallback.
         */
        fun create(context: Context, passphrase: ByteArray): WealthDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, WealthDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}
