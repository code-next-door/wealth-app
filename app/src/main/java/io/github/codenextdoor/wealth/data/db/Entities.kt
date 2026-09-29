package io.github.codenextdoor.wealth.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.codenextdoor.wealth.domain.AssetKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Room entities = database tables. They stay in the data layer; the rest of
// the app uses the domain models in io.github.codenextdoor.wealth.domain.

// Entities are @Serializable because backups write them as they are (see
// BackupSnapshot): a new field needs a default, so older backups still read.
@Entity(tableName = "currencies")
@Serializable
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
@Serializable
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerialName("from") val fromCode: String,
    @SerialName("to") val toCode: String,
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
@Serializable
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
@Serializable
data class AccountTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String?,
    val name: String,
    val kind: AssetKind,
    val countryId: Long?,
    val sortOrder: Int,
    /** Accounts of this type hold shares: number of shares × price, plus cash. */
    @ColumnInfo(defaultValue = "0") val holdsShares: Boolean = false,
)

@Entity(tableName = "expense_categories")
@Serializable
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String?,
    val name: String,
    val sortOrder: Int,
    /**
     * False for money that only moves between the user's own accounts (e.g.
     * to a broker): already in net worth, so the Spending tab leaves it out.
     */
    @ColumnInfo(defaultValue = "1") val countsAsSpending: Boolean = true,
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
@Serializable
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accountTypeId: Long,
    @SerialName("currency") val currencyCode: String,
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
    /** Ticker symbol (e.g. "GOOG") for accounts holding shares; null otherwise. */
    val shareSymbol: String? = null,
    /** Cached shares held in the latest entry, as exact decimal text. Null for other accounts. */
    val units: String? = null,
    /** Chosen by the user: kept and listed, but not counted in net worth (DB v11). */
    @ColumnInfo(defaultValue = "0") val excludedFromNetWorth: Boolean = false,
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
@Serializable
data class BalanceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Epoch day (days since 1970-01-01). */
    val date: Long,
    /** Minor units, same meaning as [AccountEntity.balanceMinor]; the cash, for accounts holding shares. */
    val balanceMinor: Long,
    /** Shares held that day, as exact decimal text; null for accounts without shares. */
    val units: String? = null,
)

/** A share's price on a day; like exchange rates, a typed price is never replaced by a downloaded one. */
@Entity(tableName = "share_prices", indices = [Index(value = ["symbol", "date"], unique = true)])
@Serializable
data class SharePriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    /** Epoch day. */
    val date: Long,
    /** Exact decimal as text. */
    val price: String,
    /** [ExchangeRateEntity.MANUAL] or [ExchangeRateEntity.FETCHED]. */
    @ColumnInfo(defaultValue = ExchangeRateEntity.MANUAL) val source: String = ExchangeRateEntity.MANUAL,
)

/** A grant of company shares that vest over time (see domain Grant). Not part of net worth. */
@Entity(
    tableName = "grants",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currencyCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("currencyCode")],
)
@Serializable
data class GrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val symbol: String,
    @SerialName("currency") val currencyCode: String,
    /** Epoch days. */
    val grantDate: Long,
    val totalUnits: String,
    val vestStart: Long,
    val vestMonths: Int,
    val intervalMonths: Int,
    val cliffMonths: Int,
    val note: String?,
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
        // Deleting a recurring expense keeps the expenses it added.
        ForeignKey(
            entity = RecurringExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("currencyCode"), Index("date"), Index("importKey"), Index("recurringId")],
)
@Serializable
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day. */
    val date: Long,
    /** Minor units; positive is money spent, negative a refund. */
    val amountMinor: Long,
    @SerialName("currency") val currencyCode: String,
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
    /** The recurring expense that added this one, if any. */
    val recurringId: Long? = null,
)

/** A house's details; its account (type "Real estate") holds its values (see domain Property). */
@Entity(
    tableName = "properties",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        // Deleting the loan account just unlinks it.
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["loanAccountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["accountId"], unique = true), Index("loanAccountId")],
)
@Serializable
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val purchasePriceMinor: Long,
    /** Epoch day. */
    val purchaseDate: Long,
    /** Yearly growth in percent, as exact decimal text (e.g. "7"). */
    val growthPercent: String,
    val loanAccountId: Long?,
)

/** An expense that repeats (see domain RecurringExpense). */
@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currencyCode"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ExpenseCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("currencyCode"), Index("categoryId"), Index("accountId")],
)
@Serializable
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amountMinor: Long,
    @SerialName("currency") val currencyCode: String,
    val categoryId: Long?,
    val accountId: Long?,
    val intervalMonths: Int,
    /** Epoch days. */
    val startDate: Long,
    val endDate: Long?,
    val lastAdded: Long?,
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
@Serializable
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored normalized (see Categorizer.normalize). */
    val keyword: String,
    val categoryId: Long?,
)

/** Simple key/value app settings, kept in the database so backups include them. */
@Entity(tableName = "settings")
@Serializable
data class SettingEntity(
    @PrimaryKey val name: String,
    val value: String,
)

object SettingKeys {
    const val BASE_CURRENCY = "base_currency"
    const val SEED_VERSION = "seed_version"

    /** "false" leaves houses (and their loans) out of net worth; missing means true. */
    const val HOUSES_IN_NET_WORTH = "houses_in_net_worth"
}
