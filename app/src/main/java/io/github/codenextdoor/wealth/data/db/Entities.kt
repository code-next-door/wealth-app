package io.github.codenextdoor.wealth.data.db

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

@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["fromCode", "toCode"],
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
    indices = [Index("toCode")],
)
data class ExchangeRateEntity(
    val fromCode: String,
    val toCode: String,
    /** Exact decimal as text (e.g. "0.0095"); never stored as a floating-point number. */
    val rate: String,
    /** Epoch milliseconds. */
    val updatedAt: Long,
)

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
