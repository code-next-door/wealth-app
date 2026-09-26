package io.github.codenextdoor.wealth.data.repository

import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.CurrencyEntity
import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.ExchangeRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/** Currencies, exchange rates and the base currency. */
class CurrencyRepository(private val db: WealthDatabase) {

    val currencies: Flow<List<Currency>> = db.currencyDao().observeAll().map { rows ->
        rows.map { Currency(it.code, it.name, it.decimals) }
    }

    val baseCurrency: Flow<String> = db.settingsDao().observe(SettingKeys.BASE_CURRENCY)
        .map { it ?: DefaultData.BASE_CURRENCY }

    val exchangeRates: Flow<List<ExchangeRate>> = db.exchangeRateDao().observeAll().map { rows ->
        rows.map {
            ExchangeRate(it.fromCode, it.toCode, BigDecimal(it.rate), Instant.ofEpochMilli(it.updatedAt))
        }
    }

    suspend fun addCurrency(currency: Currency) {
        val dao = db.currencyDao()
        dao.insert(CurrencyEntity(currency.code, currency.name, currency.decimals, dao.nextSortOrder()))
    }

    /** Also deletes the currency's exchange rates (database cascade). */
    suspend fun deleteCurrency(code: String) = db.currencyDao().delete(code)

    suspend fun setBaseCurrency(code: String) =
        db.settingsDao().put(SettingEntity(SettingKeys.BASE_CURRENCY, code))

    /** Saves "1 [from] = [rate] [to]", replacing any rate entered the other way round. */
    suspend fun setRate(from: String, to: String, rate: BigDecimal) {
        db.withTransaction {
            val dao = db.exchangeRateDao()
            dao.delete(from = to, to = from)
            dao.upsert(ExchangeRateEntity(from, to, rate.toPlainString(), System.currentTimeMillis()))
        }
    }
}
