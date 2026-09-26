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
    @Query("SELECT * FROM exchange_rates")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Upsert
    suspend fun upsert(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates WHERE fromCode = :from AND toCode = :to")
    suspend fun delete(from: String, to: String)
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
