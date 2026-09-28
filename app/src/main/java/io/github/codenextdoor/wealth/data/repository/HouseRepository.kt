package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.PropertyEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** What the house form edits: the house's account and its details together. */
data class HouseDetails(
    /** 0 for a new house, else its account (possibly an existing "Real estate" account). */
    val accountId: Long,
    val name: String,
    val currencyCode: String,
    val countryId: Long?,
    val purchasePriceMinor: Long,
    val purchaseDate: LocalDate,
    val growthPercent: BigDecimal,
    val loanAccountId: Long?,
    val institution: String? = null,
    val note: String? = null,
)

/**
 * Houses: each is a "Real estate" account (its balance entries are the known
 * values: the purchase and any valuations) plus a `properties` row.
 */
class HouseRepository(private val db: WealthDatabase, private val accounts: AccountRepository) {

    val houses: Flow<List<Property>> = db.propertyDao().observeAll().map { rows ->
        rows.map { Property(it.id, it.accountId, it.purchasePriceMinor, LocalDate.ofEpochDay(it.purchaseDate), BigDecimal(it.growthPercent), it.loanAccountId) }
    }

    /** Whether houses (and their linked loans) count in net worth. */
    val inNetWorth: Flow<Boolean> = db.settingsDao().observe(SettingKeys.HOUSES_IN_NET_WORTH).map { it != "false" }

    suspend fun setInNetWorth(value: Boolean) = db.settingsDao().put(SettingEntity(SettingKeys.HOUSES_IN_NET_WORTH, value.toString()))

    suspend fun forAccount(accountId: Long): Property? = houses.first().firstOrNull { it.accountId == accountId }

    /**
     * Creates or updates a house. The purchase is stored as the account's
     * balance on the purchase date (moved if that date changes); other
     * entries (valuations) are kept. Returns the house's account id.
     */
    suspend fun save(details: HouseDetails): Long = db.withTransaction {
        val existing = if (details.accountId == 0L) null else accounts.get(details.accountId)
        val typeId = existing?.accountTypeId ?: realEstateTypeId()
        val account = Account(
            id = existing?.id ?: 0,
            name = details.name,
            accountTypeId = typeId,
            currencyCode = details.currencyCode,
            countryId = details.countryId,
            balanceMinor = details.purchasePriceMinor,
            balanceUpdatedAt = Instant.now(),
            institution = details.institution,
            note = details.note,
        )
        val previous = db.propertyDao().forAccount(existing?.id ?: -1)
        val id = accounts.save(account, balanceDate = details.purchaseDate, recordBalance = true)
        // The purchase moved to another day: drop the old purchase value.
        val oldPurchase = previous?.purchaseDate?.let(LocalDate::ofEpochDay)
        if (oldPurchase != null && oldPurchase != details.purchaseDate) {
            accounts.observeHistory(id).first().firstOrNull { it.date == oldPurchase }?.let { accounts.deleteHistoryEntry(it.id) }
        }
        db.propertyDao().upsert(
            PropertyEntity(
                id = previous?.id ?: 0,
                accountId = id,
                purchasePriceMinor = details.purchasePriceMinor,
                purchaseDate = details.purchaseDate.toEpochDay(),
                growthPercent = details.growthPercent.stripTrailingZeros().toPlainString(),
                loanAccountId = details.loanAccountId,
            ),
        )
        id
    }

    /** Deletes the house, its account and values; a linked loan stays. */
    suspend fun delete(accountId: Long) = accounts.delete(accountId)

    /** The seeded "Real estate" type, or any asset type if the user deleted it. */
    private suspend fun realEstateTypeId(): Long {
        val types = db.accountTypeDao().observeAll().first()
        return (types.firstOrNull { it.seedKey == "real_estate" } ?: types.first { it.kind == AssetKind.ASSET }).id
    }
}
