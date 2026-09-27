package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import io.github.codenextdoor.wealth.data.db.GrantEntity
import io.github.codenextdoor.wealth.data.db.SharePriceEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.PricePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

/** Share prices and stock grants. */
class ShareRepository(private val db: WealthDatabase) {

    val prices: Flow<PriceBook> = db.sharePriceDao().observeAll().map { rows ->
        PriceBook(rows.map { PricePoint(it.symbol, LocalDate.ofEpochDay(it.date), BigDecimal(it.price), it.source == ExchangeRateEntity.FETCHED) })
    }

    val grants: Flow<List<Grant>> = db.grantDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Saves the price of one [symbol] share on [date], replacing any price for
     * that day. The user's choice, so it replaces even a typed price; [fetched]
     * marks that they chose the downloaded one.
     */
    suspend fun setPrice(symbol: String, date: LocalDate, price: BigDecimal, fetched: Boolean = false) =
        db.sharePriceDao().upsert(entity(symbol, date, price, fetched))

    /** Saves downloaded prices, except on days the user typed one. Returns how many were saved. */
    suspend fun saveFetchedPrices(symbol: String, prices: Map<LocalDate, BigDecimal>): Int = db.withTransaction {
        val dao = db.sharePriceDao()
        prices.count { (date, price) ->
            val typed = dao.onDay(symbol, date.toEpochDay())?.source == ExchangeRateEntity.MANUAL
            if (!typed) dao.upsert(entity(symbol, date, price, fetched = true))
            !typed
        }
    }

    /** The last day with a downloaded price for [symbol], to download only newer ones. */
    suspend fun latestFetchedDay(symbol: String): LocalDate? = db.sharePriceDao().latestFetchedDay(symbol)?.let(LocalDate::ofEpochDay)

    suspend fun earliestFetchedDay(symbol: String): LocalDate? = db.sharePriceDao().earliestFetchedDay(symbol)?.let(LocalDate::ofEpochDay)

    suspend fun getGrant(id: Long): Grant? = db.grantDao().get(id)?.toDomain()

    /** Inserts when [Grant.id] is 0, otherwise updates. Returns the id. */
    suspend fun saveGrant(grant: Grant): Long {
        val id = db.grantDao().upsert(
            GrantEntity(
                id = grant.id,
                name = grant.name,
                symbol = grant.symbol,
                currencyCode = grant.currencyCode,
                grantDate = grant.grantDate.toEpochDay(),
                totalUnits = grant.totalUnits.toPlainString(),
                vestStart = grant.vestStart.toEpochDay(),
                vestMonths = grant.vestMonths,
                intervalMonths = grant.intervalMonths,
                cliffMonths = grant.cliffMonths,
                note = grant.note,
            ),
        )
        return if (grant.id == 0L) id else grant.id
    }

    suspend fun deleteGrant(id: Long) = db.grantDao().delete(id)

    private fun entity(symbol: String, date: LocalDate, price: BigDecimal, fetched: Boolean) = SharePriceEntity(
        symbol = symbol,
        date = date.toEpochDay(),
        price = price.stripTrailingZeros().toPlainString(),
        source = if (fetched) ExchangeRateEntity.FETCHED else ExchangeRateEntity.MANUAL,
    )

    private fun GrantEntity.toDomain() = Grant(
        id = id,
        name = name,
        symbol = symbol,
        currencyCode = currencyCode,
        grantDate = LocalDate.ofEpochDay(grantDate),
        totalUnits = BigDecimal(totalUnits),
        vestStart = LocalDate.ofEpochDay(vestStart),
        vestMonths = vestMonths,
        intervalMonths = intervalMonths,
        cliffMonths = cliffMonths,
        note = note,
    )
}
