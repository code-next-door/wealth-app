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
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.RatePoint
import java.time.LocalDate

/** Currencies, exchange rates and the base currency. */
class CurrencyRepository(private val db: WealthDatabase) {

    val currencies: Flow<List<Currency>> = db.currencyDao().observeAll().map { rows ->
        rows.map { Currency(it.code, it.name, it.decimals) }
    }

    val baseCurrency: Flow<String> = db.settingsDao().observe(SettingKeys.BASE_CURRENCY)
        .map { it ?: DefaultData.BASE_CURRENCY }

    /** Every rate the user has entered, with its date. */
    val rateBook: Flow<RateBook> = db.exchangeRateDao().observeAll().map { rows ->
        RateBook(rows.map { RatePoint(it.fromCode, it.toCode, BigDecimal(it.rate), LocalDate.ofEpochDay(it.date)) })
    }

    /** The latest rate for each currency pair. */
    val exchangeRates: Flow<List<ExchangeRate>> = rateBook.map { it.currentRates }

    suspend fun addCurrency(currency: Currency) {
        val dao = db.currencyDao()
        dao.insert(CurrencyEntity(currency.code, currency.name, currency.decimals, dao.nextSortOrder()))
    }

    /**
     * Also deletes the currency's exchange rates (database cascade).
     * Returns false (and deletes nothing) if accounts or expenses still use it.
     */
    suspend fun deleteCurrency(code: String): Boolean {
        if (db.accountDao().countWithCurrency(code) > 0 || db.expenseDao().countWithCurrency(code) > 0) return false
        db.currencyDao().delete(code)
        return true
    }

    suspend fun setBaseCurrency(code: String) =
        db.settingsDao().put(SettingEntity(SettingKeys.BASE_CURRENCY, code))

    /**
     * Saves "1 [from] = [rate] [to]" as the rate from [date] onwards, replacing
     * any rate for the same pair and day (in either direction).
     */
    suspend fun setRate(from: String, to: String, rate: BigDecimal, date: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val dao = db.exchangeRateDao()
            dao.delete(from = to, to = from, date = date.toEpochDay())
            dao.upsert(ExchangeRateEntity(fromCode = from, toCode = to, date = date.toEpochDay(), rate = rate.toPlainString()))
        }
    }
}
