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
        ExpenseEntity::class,
        CategoryRuleEntity::class,
        SharePriceEntity::class,
        GrantEntity::class,
    ],
    version = 8,
    exportSchema = true,
    // Upgrades existing installs without losing data. Room generates the SQL
    // by comparing the committed schema files (app/schemas/.../N.json).
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Adds the accounts table.
        AutoMigration(from = 4, to = 5), // Adds expenses and category rules.
        AutoMigration(from = 6, to = 7), // Rates remember whether they were typed or fetched.
        AutoMigration(from = 7, to = 8), // Accounts holding shares, share prices, stock grants.
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
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun backupDao(): BackupDao
    abstract fun sharePriceDao(): SharePriceDao
    abstract fun grantDao(): GrantDao

    companion object {
        private const val FILE_NAME = "wealth.db"

        /**
         * Opens the database file, encrypted with SQLCipher using [passphrase].
         * Schema changes must add a Migration here — never a destructive fallback.
         */
        fun create(context: Context, passphrase: ByteArray, fileName: String = FILE_NAME): WealthDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, WealthDatabase::class.java, fileName)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*Migrations.ALL)
                .build()
        }

        /** Unencrypted, in memory, gone when the process ends. For tests only. */
        fun createInMemory(context: Context): WealthDatabase =
            Room.inMemoryDatabaseBuilder(context, WealthDatabase::class.java).build()
    }
}
