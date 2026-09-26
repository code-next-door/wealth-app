package io.github.codenextdoor.wealth.data.db

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class WealthDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun countryDao(): CountryDao
    abstract fun accountTypeDao(): AccountTypeDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun settingsDao(): SettingsDao

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
                .build()
        }
    }
}
