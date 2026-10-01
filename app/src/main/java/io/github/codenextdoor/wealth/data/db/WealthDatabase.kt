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
        RecurringExpenseEntity::class,
        PropertyEntity::class,
        RemovedImportEntity::class,
        LoanEntity::class,
        LoanRateChangeEntity::class,
        ExpensePartEntity::class,
        PensionEntity::class,
    ],
    version = 16,
    exportSchema = true,
    // Upgrades existing installs without losing data. Room generates the SQL
    // by comparing the committed schema files (app/schemas/.../N.json).
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // Adds the accounts table.
        AutoMigration(from = 4, to = 5), // Adds expenses and category rules.
        AutoMigration(from = 6, to = 7), // Rates remember whether they were typed or fetched.
        AutoMigration(from = 7, to = 8), // Accounts holding shares, share prices, stock grants.
        AutoMigration(from = 8, to = 9), // Recurring expenses.
        AutoMigration(from = 9, to = 10), // Houses (a new table only).
        AutoMigration(from = 10, to = 11), // Accounts can be left out of net worth (default: counted).
        AutoMigration(from = 11, to = 12), // Categories can be left out of spending (default: counted).
        AutoMigration(from = 12, to = 13), // Income categories (default: spending); deleted imports remembered.
        AutoMigration(from = 13, to = 14), // Calculated loans: their terms and rate changes; loan types.
        AutoMigration(from = 14, to = 15), // Split expenses: their extra parts (a new table only).
        AutoMigration(from = 15, to = 16), // Pensions growing with contributions: their terms; such types.
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
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun propertyDao(): PropertyDao
    abstract fun loanDao(): LoanDao
    abstract fun pensionDao(): PensionDao

    companion object {
        const val FILE_NAME = "wealth.db"

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

        /**
         * Like [create], but opens the file right away: SQLCipher checks the key and the
         * migrations run now, so a lost key is noticed at start (not on some later query).
         */
        fun createChecked(context: Context, passphrase: ByteArray, fileName: String = FILE_NAME): WealthDatabase {
            val db = create(context, passphrase, fileName)
            try {
                db.openHelper.writableDatabase
            } catch (e: Exception) {
                db.close()
                throw e
            }
            return db
        }

        /** Unencrypted, in memory, gone when the process ends. For tests only. */
        fun createInMemory(context: Context): WealthDatabase =
            Room.inMemoryDatabaseBuilder(context, WealthDatabase::class.java).build()
    }
}
