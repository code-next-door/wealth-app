package io.github.codenextdoor.wealth.data.repository

import org.junit.Assert.assertFalse
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val categories = catalog.expenseCategories.first()
        assertEquals(DefaultData.expenseCategories.size + 1, categories.size) // + "Transfers & investments"
        assertEquals(listOf("Transfers & investments"), categories.filterNot { it.countsAsSpending }.map { it.name })
        assertTrue(categories.none { it.name == "Other" })

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
    fun upgradingFromVersion4RemovesOtherAndAddsTransfers() = runBlocking {
        // An install from before: the default "Other" (with an expense and a rule), no transfers category.
        val dao = db.expenseCategoryDao()
        dao.delete(categoryId("transfers"))
        val other = dao.insert(ExpenseCategoryEntity(seedKey = "other", name = "Other", sortOrder = dao.nextSortOrder()))
        expenses.save(expense("Haircut", 30_00, categoryId = other, locked = true))
        expenses.saveRule(null, "BARBER", other)
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "4"))

        assertFalse(DatabaseSeeder(db, context).seedIfNeeded())

        val categories = catalog.expenseCategories.first()
        assertTrue(categories.none { it.id == other })
        assertNull(db.backupDao().expenses().single { it.description == "Haircut" }.categoryId) // kept, uncategorized
        assertTrue(expenses.rules().none { it.keyword == "BARBER" })
        assertEquals(1, categories.count { it.name == "Transfers & investments" && !it.countsAsSpending })
    }

    @Test
    fun aRenamedOtherCategoryIsTheUsersOwnAndStays() = runBlocking {
        val dao = db.expenseCategoryDao()
        val renamed = dao.insert(ExpenseCategoryEntity(seedKey = "other", name = "Pets", sortOrder = dao.nextSortOrder()))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "4"))

        DatabaseSeeder(db, context).seedIfNeeded()

        val categories = catalog.expenseCategories.first()
        assertTrue(categories.any { it.id == renamed && it.name == "Pets" })
        assertEquals(1, categories.count { it.name == "Transfers & investments" }) // not added twice
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
