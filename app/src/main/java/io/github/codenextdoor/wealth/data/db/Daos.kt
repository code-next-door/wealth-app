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

    /** Rates between [a] and [b], in either direction, for one day. */
    @Query(
        "SELECT * FROM exchange_rate_history WHERE date = :date AND " +
            "((fromCode = :a AND toCode = :b) OR (fromCode = :b AND toCode = :a))",
    )
    suspend fun onDay(a: String, b: String, date: Long): List<ExchangeRateEntity>
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

    @Query("SELECT * FROM expense_categories")
    suspend fun getAll(): List<ExpenseCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM expense_categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(category: ExpenseCategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<ExpenseCategoryEntity>)

    @Query("UPDATE expense_categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE expense_categories SET countsAsSpending = :counts WHERE id = :id")
    suspend fun setCountsAsSpending(id: Long, counts: Boolean)

    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE date BETWEEN :fromDay AND :toDay ORDER BY date DESC, id DESC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    /** Epoch day of the oldest expense; null without any. */
    @Query("SELECT MIN(date) FROM expenses")
    fun observeEarliestDate(): Flow<Long?>

    @Query("SELECT * FROM expenses WHERE categoryLocked = 0")
    suspend fun unlocked(): List<ExpenseEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("UPDATE expenses SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT importKey FROM expenses WHERE importKey IN (:keys)")
    suspend fun existingImportKeys(keys: List<String>): List<String>

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT COUNT(*) FROM expenses WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int

    /** Expenses added by recurring expenses between two days (epoch days, inclusive). */
    @Query("SELECT * FROM expenses WHERE recurringId IS NOT NULL AND date BETWEEN :fromDay AND :toDay")
    suspend fun recurringBetween(fromDay: Long, toDay: Long): List<ExpenseEntity>
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY keyword")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRuleEntity>

    /** A keyword maps to one category; saving an existing keyword replaces its rule. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringExisting(rules: List<CategoryRuleEntity>)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties")
    fun observeAll(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE accountId = :accountId")
    suspend fun forAccount(accountId: Long): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(property: PropertyEntity): Long
}

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY description COLLATE NOCASE")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAll(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun get(id: Long): RecurringExpenseEntity?

    @Insert
    suspend fun insert(item: RecurringExpenseEntity): Long

    @Update
    suspend fun update(item: RecurringExpenseEntity)

    @Query("UPDATE recurring_expenses SET lastAdded = :day WHERE id = :id")
    suspend fun setLastAdded(id: Long, day: Long)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM recurring_expenses WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int
}

@Dao
interface SharePriceDao {
    @Query("SELECT * FROM share_prices ORDER BY date")
    fun observeAll(): Flow<List<SharePriceEntity>>

    @Query("SELECT * FROM share_prices WHERE symbol = :symbol AND date = :date")
    suspend fun onDay(symbol: String, date: Long): SharePriceEntity?

    /** Replaces any price for the same share and day (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(price: SharePriceEntity)

    @Query("SELECT MAX(date) FROM share_prices WHERE symbol = :symbol AND source = 'fetched'")
    suspend fun latestFetchedDay(symbol: String): Long?

    @Query("SELECT MIN(date) FROM share_prices WHERE symbol = :symbol AND source = 'fetched'")
    suspend fun earliestFetchedDay(symbol: String): Long?
}

@Dao
interface GrantDao {
    @Query("SELECT * FROM grants ORDER BY grantDate, name")
    fun observeAll(): Flow<List<GrantEntity>>

    @Query("SELECT * FROM grants WHERE id = :id")
    suspend fun get(id: Long): GrantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: GrantEntity): Long

    @Query("DELETE FROM grants WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM grants WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int
}

/** Reads and replaces everything at once, for backups. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM currencies") suspend fun currencies(): List<CurrencyEntity>
    @Query("SELECT * FROM exchange_rate_history") suspend fun exchangeRates(): List<ExchangeRateEntity>
    @Query("SELECT * FROM countries") suspend fun countries(): List<CountryEntity>
    @Query("SELECT * FROM account_types") suspend fun accountTypes(): List<AccountTypeEntity>
    @Query("SELECT * FROM expense_categories") suspend fun expenseCategories(): List<ExpenseCategoryEntity>
    @Query("SELECT * FROM category_rules") suspend fun categoryRules(): List<CategoryRuleEntity>
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM balance_entries") suspend fun balanceEntries(): List<BalanceEntryEntity>
    @Query("SELECT * FROM expenses") suspend fun expenses(): List<ExpenseEntity>
    @Query("SELECT * FROM settings") suspend fun settings(): List<SettingEntity>
    @Query("SELECT * FROM share_prices") suspend fun sharePrices(): List<SharePriceEntity>
    @Query("SELECT * FROM grants") suspend fun grants(): List<GrantEntity>
    @Query("SELECT * FROM recurring_expenses") suspend fun recurringExpenses(): List<RecurringExpenseEntity>
    @Query("SELECT * FROM properties") suspend fun properties(): List<PropertyEntity>

    // Children before parents, so foreign keys are never violated.
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM recurring_expenses") suspend fun clearRecurringExpenses()
    @Query("DELETE FROM properties") suspend fun clearProperties()
    @Query("DELETE FROM grants") suspend fun clearGrants()
    @Query("DELETE FROM share_prices") suspend fun clearSharePrices()
    @Query("DELETE FROM category_rules") suspend fun clearCategoryRules()
    @Query("DELETE FROM balance_entries") suspend fun clearBalanceEntries()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM exchange_rate_history") suspend fun clearExchangeRates()
    @Query("DELETE FROM account_types") suspend fun clearAccountTypes()
    @Query("DELETE FROM expense_categories") suspend fun clearExpenseCategories()
    @Query("DELETE FROM countries") suspend fun clearCountries()
    @Query("DELETE FROM currencies") suspend fun clearCurrencies()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Insert suspend fun insertCurrencies(items: List<CurrencyEntity>)
    @Insert suspend fun insertExchangeRates(items: List<ExchangeRateEntity>)
    @Insert suspend fun insertCountries(items: List<CountryEntity>)
    @Insert suspend fun insertAccountTypes(items: List<AccountTypeEntity>)
    @Insert suspend fun insertExpenseCategories(items: List<ExpenseCategoryEntity>)
    @Insert suspend fun insertCategoryRules(items: List<CategoryRuleEntity>)
    @Insert suspend fun insertAccounts(items: List<AccountEntity>)
    @Insert suspend fun insertBalanceEntries(items: List<BalanceEntryEntity>)
    @Insert suspend fun insertExpenses(items: List<ExpenseEntity>)
    @Insert suspend fun insertSettings(items: List<SettingEntity>)
    @Insert suspend fun insertSharePrices(items: List<SharePriceEntity>)
    @Insert suspend fun insertGrants(items: List<GrantEntity>)
    @Insert suspend fun insertRecurringExpenses(items: List<RecurringExpenseEntity>)
    @Insert suspend fun insertProperties(items: List<PropertyEntity>)
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

    @Query("UPDATE accounts SET balanceMinor = :balanceMinor, balanceUpdatedAt = :updatedAt, units = :units WHERE id = :id")
    suspend fun updateCachedBalance(id: Long, balanceMinor: Long, updatedAt: Long, units: String?)

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
