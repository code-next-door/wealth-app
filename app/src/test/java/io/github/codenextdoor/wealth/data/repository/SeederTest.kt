package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.data.db.SettingKeys
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import io.github.codenextdoor.wealth.data.seed.DefaultData
import io.github.codenextdoor.wealth.domain.AssetKind
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
        assertEquals(DefaultData.accountTypes.size, types.size)
        assertEquals(3, types.count { it.kind == AssetKind.LIABILITY })
        assertEquals(DefaultData.expenseCategories.size, catalog.expenseCategories.first().size)

        val rules = expenses.rules()
        assertEquals(DefaultData.skipImportKeywords.size, rules.count { it.skipsImport })
        assertTrue(rules.any { it.keyword == "MIGROS" && it.categoryId == categoryId("groceries") })
        assertEquals(DefaultData.SEED_VERSION.toString(), db.settingsDao().get(SettingKeys.SEED_VERSION))
    }

    @Test
    fun runningAgainAddsNothing() = runBlocking {
        val before = db.backupDao().let { listOf(it.currencies().size, it.accountTypes().size, it.categoryRules().size) }
        DatabaseSeeder(db, context).seedIfNeeded()
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

        DatabaseSeeder(db, context).seedIfNeeded()

        val rules = expenses.rules()
        assertEquals(categoryId("shopping"), rules.single { it.keyword == "COOP" }.categoryId) // user's rule kept
        assertTrue(rules.any { it.keyword == "MIGROS" }) // defaults added
        assertTrue(rules.any { it.skipsImport })
        assertEquals(typesBefore, catalog.accountTypes.first().size) // v1 data not duplicated
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
