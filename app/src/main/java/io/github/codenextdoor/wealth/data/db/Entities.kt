package io.github.codenextdoor.wealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.codenextdoor.wealth.domain.AssetKind

// Room entities = database tables. They stay in the data layer; the rest of
// the app uses the domain models in io.github.codenextdoor.wealth.domain.

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val decimals: Int,
    val sortOrder: Int,
)

/**
 * "1 [fromCode] = [rate] [toCode]" as of [date]. Rates form a history: the
 * rate for any day is the latest one on or before it, and the current rate
 * is simply the latest. One entry per pair and day.
 */
@Entity(
    tableName = "exchange_rate_history",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["fromCode"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["toCode"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["fromCode", "toCode", "date"], unique = true), Index("toCode")],
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCode: String,
    val toCode: String,
    /** Epoch day the rate applies from. */
    val date: Long,
    /** Exact decimal as text (e.g. "0.0095"); never stored as a floating-point number. */
    val rate: String,
    /**
     * [MANUAL] when the user typed it, [FETCHED] when downloaded. A typed rate
     * is never replaced by a downloaded one.
     */
    @ColumnInfo(defaultValue = MANUAL) val source: String = MANUAL,
) {
    companion object {
        const val MANUAL = "manual"
        const val FETCHED = "fetched"
    }
}

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identifies rows created from default data; null for user-created rows. */
    val seedKey: String?,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "account_types",
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["id"],
            childColumns = ["countryId"],
            // Deleting a country moves its account types to "General".
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("countryId")],
)
data class AccountTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String?,
    val name: String,
    val kind: AssetKind,
    val countryId: Long?,
    val sortOrder: Int,
)

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String?,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        // RESTRICT: a type or currency can't be deleted while an account uses it.
        ForeignKey(
            entity = AccountTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currencyCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["id"],
            childColumns = ["countryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountTypeId"), Index("currencyCode"), Index("countryId")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accountTypeId: Long,
    val currencyCode: String,
    /** Null means "General" (no country). */
    val countryId: Long?,
    /** Minor units (cents/paise). For liabilities: the amount owed, as a positive number. */
    val balanceMinor: Long,
    /**
     * Cached copy of the latest [BalanceEntryEntity] (by date), so lists don't
     * need to scan history. Epoch milliseconds of that entry's date.
     */
    val balanceUpdatedAt: Long,
    val institution: String?,
    val note: String?,
)

/**
 * An account's balance on a given day. Together these form the history used
 * for net worth charts and trends. At most one entry per account per day.
 */
@Entity(
    tableName = "balance_entries",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId", "date"], unique = true)],
)
data class BalanceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Epoch day (days since 1970-01-01). */
    val date: Long,
    /** Minor units, same meaning as [AccountEntity.balanceMinor]. */
    val balanceMinor: Long,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // Deleting a category leaves its expenses uncategorized.
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currencyCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("currencyCode"), Index("date"), Index("importKey")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day. */
    val date: Long,
    /** Minor units; positive is money spent, negative a refund. */
    val amountMinor: Long,
    val currencyCode: String,
    /** What the statement (or user) calls it, e.g. "COOP-1234 ZUERICH". Used for categorization. */
    val description: String,
    val categoryId: Long?,
    /** True when the user chose the category; rules then never change it. */
    val categoryLocked: Boolean,
    val accountId: Long?,
    val note: String?,
    /** Epoch millis. */
    val createdAt: Long,
    /** Fingerprint of the statement row this came from; prevents importing it twice. Null for manual entries. */
    val importKey: String? = null,
)

/**
 * "Statement text containing [keyword] belongs to [categoryId]". A null
 * category means "don't import" (e.g. paying the credit card bill, or moving
 * money between your own accounts).
 */
@Entity(
    tableName = "category_rules",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["keyword"], unique = true), Index("categoryId")],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored normalized (see Categorizer.normalize). */
    val keyword: String,
    val categoryId: Long?,
)

/** Simple key/value app settings, kept in the database so backups include them. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val name: String,
    val value: String,
)

object SettingKeys {
    const val BASE_CURRENCY = "base_currency"
    const val SEED_VERSION = "seed_version"
}
