package io.github.codenextdoor.wealth.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// DAOs ("data access objects") hold the SQL queries. Room generates the code.
// Functions returning Flow re-emit automatically whenever the table changes.

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY sortOrder, code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM currencies")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Query("DELETE FROM currencies WHERE code = :code")
    suspend fun delete(code: String)
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rate_history ORDER BY date")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    /** Replaces any rate for the same pair and day (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rate_history WHERE fromCode = :from AND toCode = :to AND date = :date")
    suspend fun delete(from: String, to: String, date: Long)
}

@Dao
interface CountryDao {
    @Query("SELECT * FROM countries ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CountryEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM countries")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(country: CountryEntity): Long

    @Query("UPDATE countries SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM countries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AccountTypeDao {
    @Query("SELECT * FROM account_types ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<AccountTypeEntity>>

    @Query("SELECT * FROM account_types WHERE id = :id")
    suspend fun get(id: Long): AccountTypeEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM account_types")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(type: AccountTypeEntity): Long

    @Insert
    suspend fun insertAll(types: List<AccountTypeEntity>)

    @Update
    suspend fun update(type: AccountTypeEntity)

    @Query("DELETE FROM account_types WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM expense_categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(category: ExpenseCategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<ExpenseCategoryEntity>)

    @Query("UPDATE expense_categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE name = :name")
    fun observe(name: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun get(name: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE accounts SET balanceMinor = :balanceMinor, balanceUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCachedBalance(id: Long, balanceMinor: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE accountTypeId = :typeId")
    suspend fun countWithType(typeId: Long): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int
}

@Dao
interface BalanceEntryDao {
    @Query("SELECT * FROM balance_entries ORDER BY accountId, date")
    fun observeAll(): Flow<List<BalanceEntryEntity>>

    @Query("SELECT * FROM balance_entries WHERE accountId = :accountId ORDER BY date DESC")
    fun observeForAccount(accountId: Long): Flow<List<BalanceEntryEntity>>

    @Query("SELECT * FROM balance_entries WHERE accountId = :accountId ORDER BY date DESC LIMIT 1")
    suspend fun latestFor(accountId: Long): BalanceEntryEntity?

    @Query("SELECT COUNT(*) FROM balance_entries WHERE accountId = :accountId")
    suspend fun countFor(accountId: Long): Int

    @Query("SELECT * FROM balance_entries WHERE id = :id")
    suspend fun get(id: Long): BalanceEntryEntity?

    /** Replaces any existing entry for the same account and day (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BalanceEntryEntity)

    @Query("DELETE FROM balance_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
