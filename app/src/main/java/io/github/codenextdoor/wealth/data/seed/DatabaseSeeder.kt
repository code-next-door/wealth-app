package io.github.codenextdoor.wealth.data.seed

import android.content.Context
import androidx.room.withTransaction
import io.github.codenextdoor.wealth.data.db.AccountTypeEntity
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.CountryEntity
import io.github.codenextdoor.wealth.data.db.CurrencyEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Categorizer
import io.github.codenextdoor.wealth.domain.IsoCurrencies
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Inserts [DefaultData]: everything on first launch, and on later launches
 * only what was added in newer versions (tracked by the seed version).
 */
class DatabaseSeeder(
    private val db: WealthDatabase,
    private val context: Context,
) {

    /**
     * Returns true for a brand-new database (nothing seeded before): the one
     * reliable sign of a fresh install, used to show onboarding only then.
     */
    suspend fun seedIfNeeded(): Boolean =
        // One transaction: either everything is seeded or nothing is.
        db.withTransaction {
            val settings = db.settingsDao()
            val version = settings.get(SettingKeys.SEED_VERSION)?.toIntOrNull() ?: 0
            if (version >= DefaultData.SEED_VERSION) return@withTransaction false
            if (version < 1) seedInitialData()
            if (version < 2) seedCategoryRules()
            if (version < 3) seedSkipRules()
            if (version < 4) seedStockPlanType()
            if (version < 5) seedTransfersCategory()
            settings.put(SettingEntity(SettingKeys.SEED_VERSION, DefaultData.SEED_VERSION.toString()))
            version == 0
        }

    private suspend fun seedTransfersCategory() {
        val dao = db.expenseCategoryDao()
        val category = DefaultData.transfersCategory
        if (dao.getAll().any { it.seedKey == category.key }) return
        dao.insert(
            ExpenseCategoryEntity(
                seedKey = category.key,
                name = context.getString(category.name),
                sortOrder = dao.nextSortOrder(),
                countsAsSpending = category.countsAsSpending,
            ),
        )
    }

    private suspend fun seedStockPlanType() {
        val dao = db.accountTypeDao()
        val type = DefaultData.stockPlanType
        if (dao.observeAll().first().any { it.seedKey == type.key }) return
        dao.insert(
            AccountTypeEntity(
                seedKey = type.key,
                name = context.getString(type.name),
                kind = type.kind,
                countryId = null,
                sortOrder = dao.nextSortOrder(),
                holdsShares = true,
            ),
        )
    }

    private suspend fun seedInitialData() {
        val settings = db.settingsDao()
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
                    countsAsSpending = category.countsAsSpending,
                )
            },
        )

        settings.put(SettingEntity(SettingKeys.BASE_CURRENCY, DefaultData.BASE_CURRENCY))
    }

    /** "Don't import" rules; keywords the user already has are kept. */
    private suspend fun seedSkipRules() {
        db.categoryRuleDao().insertAllIgnoringExisting(
            DefaultData.skipImportKeywords.map { CategoryRuleEntity(keyword = Categorizer.normalize(it), categoryId = null) },
        )
    }

    /** Rules for default categories that still exist; keywords the user already has are kept. */
    private suspend fun seedCategoryRules() {
        val categoryIds = db.expenseCategoryDao().getAll()
            .mapNotNull { category -> category.seedKey?.let { it to category.id } }
            .toMap()
        db.categoryRuleDao().insertAllIgnoringExisting(
            DefaultData.categoryRules.mapNotNull { (keyword, categoryKey) ->
                categoryIds[categoryKey]?.let { CategoryRuleEntity(keyword = Categorizer.normalize(keyword), categoryId = it) }
            },
        )
    }
}
