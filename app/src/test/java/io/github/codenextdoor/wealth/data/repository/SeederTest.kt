package io.github.codenextdoor.wealth.data.repository

import org.junit.Assert.assertFalse
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeederTest : DatabaseTest() {

    @Test
    fun seedsAllDefaults() = runBlocking {
        assertEquals(listOf("CHF", "INR", "USD"), currencies.currencies.first().map { it.code })
        assertEquals("CHF", currencies.baseCurrency.first())
        assertEquals(listOf("Switzerland", "India"), catalog.countries.first().map { it.name })

        val types = catalog.accountTypes.first()
        assertEquals(DefaultData.accountTypes.size + 1, types.size) // + the stock plan type
        assertEquals(1, types.count { it.holdsShares })
        assertEquals(3, types.count { it.kind == AssetKind.LIABILITY })
        assertEquals(DefaultData.expenseCategories.size, catalog.expenseCategories.first().size)

        val rules = expenses.rules()
        assertEquals(DefaultData.skipImportKeywords.size, rules.count { it.skipsImport })
        assertTrue(rules.any { it.keyword == "MIGROS" && it.categoryId == categoryId("groceries") })
        assertEquals(DefaultData.SEED_VERSION.toString(), db.settingsDao().get(SettingKeys.SEED_VERSION))
    }

    @Test
    fun onlyABrandNewDatabaseCountsAsAFreshInstall() = runBlocking<Unit> {
        val fresh = Room.inMemoryDatabaseBuilder(context, WealthDatabase::class.java).allowMainThreadQueries().build()
        try {
            assertTrue(DatabaseSeeder(fresh, context).seedIfNeeded())
            assertFalse(DatabaseSeeder(fresh, context).seedIfNeeded()) // the next start
        } finally {
            fresh.close()
        }
    }

    @Test
    fun runningAgainAddsNothing() = runBlocking {
        val before = db.backupDao().let { listOf(it.currencies().size, it.accountTypes().size, it.categoryRules().size) }
        assertFalse(DatabaseSeeder(db, context).seedIfNeeded())
        val after = db.backupDao().let { listOf(it.currencies().size, it.accountTypes().size, it.categoryRules().size) }
        assertEquals(before, after)
    }

    @Test
    fun upgradingFromVersion1AddsOnlyRulesAndKeepsUserChoices() = runBlocking {
        // Simulate an install from before rules existed, where the user has their own "COOP" rule.
        db.backupDao().clearCategoryRules()
        db.categoryRuleDao().upsert(CategoryRuleEntity(keyword = "COOP", categoryId = categoryId("shopping")))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "1"))
        val typesBefore = catalog.accountTypes.first().size

        assertFalse(DatabaseSeeder(db, context).seedIfNeeded()) // an update, not a fresh install

        val rules = expenses.rules()
        assertEquals(categoryId("shopping"), rules.single { it.keyword == "COOP" }.categoryId) // user's rule kept
        assertTrue(rules.any { it.keyword == "MIGROS" }) // defaults added
        assertTrue(rules.any { it.skipsImport })
        assertEquals(typesBefore, catalog.accountTypes.first().size) // v1 data not duplicated
    }

    @Test
    fun transfersCategoryIsSeededOutOfSpending() = runBlocking {
        val categories = catalog.expenseCategories.first()
        assertEquals(listOf("Investments & transfers"), categories.filterNot { it.countsAsSpending }.map { it.name })
        assertTrue(categories.filter { it.name != "Investments & transfers" }.all { it.countsAsSpending })
    }

    @Test
    fun upgradingFromVersion4AddsTheTransfersCategoryOnce() = runBlocking {
        // An install from before it existed: user categories are kept, it's added at the end.
        catalog.deleteExpenseCategory(categoryId(DefaultData.transfersCategory.key))
        catalog.renameExpenseCategory(categoryId("groceries"), "Food")
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "4"))

        assertFalse(DatabaseSeeder(db, context).seedIfNeeded())
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "4"))
        DatabaseSeeder(db, context).seedIfNeeded()

        val categories = catalog.expenseCategories.first()
        assertEquals(DefaultData.expenseCategories.size, categories.size)
        assertEquals(ExpenseCategory(categoryId(DefaultData.transfersCategory.key), "Investments & transfers", countsAsSpending = false), categories.last())
        assertTrue(categories.any { it.name == "Food" })
    }

    @Test
    fun rulesForDeletedDefaultCategoriesAreSkipped() = runBlocking {
        db.backupDao().clearCategoryRules()
        catalog.deleteExpenseCategory(categoryId("groceries"))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "1"))
        DatabaseSeeder(db, context).seedIfNeeded()
        assertTrue(expenses.rules().none { it.keyword == "MIGROS" })
    }
}
