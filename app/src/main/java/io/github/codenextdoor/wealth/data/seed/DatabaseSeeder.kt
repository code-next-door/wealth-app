package io.github.codenextdoor.wealth.data.seed

import android.content.Context
import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.AccountTypeEntity
import io.github.codenextdoor.wealth.data.db.CountryEntity
import io.github.codenextdoor.wealth.data.db.CurrencyEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.IsoCurrencies
import java.util.Locale

/** Inserts [DefaultData] the first time the app runs. */
class DatabaseSeeder(
    private val db: WealthDatabase,
    private val context: Context,
) {

    suspend fun seedIfNeeded() {
        // One transaction: either everything is seeded or nothing is.
        db.withTransaction {
            val settings = db.settingsDao()
            if (settings.get(SettingKeys.SEED_VERSION) != null) return@withTransaction

            val locale = Locale.getDefault()
            db.currencyDao().insertAll(
                DefaultData.currencyCodes.mapIndexedNotNull { index, code ->
                    IsoCurrencies.lookup(code, locale)?.let {
                        CurrencyEntity(it.code, it.name, it.decimals, sortOrder = index)
                    }
                },
            )

            val countryIds = DefaultData.countries.mapIndexed { index, country ->
                country.key to db.countryDao().insert(
                    CountryEntity(
                        seedKey = country.key,
                        name = context.getString(country.name),
                        sortOrder = index,
                    ),
                )
            }.toMap()

            db.accountTypeDao().insertAll(
                DefaultData.accountTypes.mapIndexed { index, type ->
                    AccountTypeEntity(
                        seedKey = type.key,
                        name = context.getString(type.name),
                        kind = type.kind,
                        countryId = type.countryKey?.let(countryIds::get),
                        sortOrder = index,
                    )
                },
            )

            db.expenseCategoryDao().insertAll(
                DefaultData.expenseCategories.mapIndexed { index, category ->
                    ExpenseCategoryEntity(
                        seedKey = category.key,
                        name = context.getString(category.name),
                        sortOrder = index,
                    )
                },
            )

            settings.put(SettingEntity(SettingKeys.BASE_CURRENCY, DefaultData.BASE_CURRENCY))
            settings.put(SettingEntity(SettingKeys.SEED_VERSION, DefaultData.SEED_VERSION.toString()))
        }
    }
}
