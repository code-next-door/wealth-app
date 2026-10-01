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
        // Card bill payments are imported into "Credit card payments" (not counted), not skipped.
        assertEquals(0, rules.count { it.categoryId == null })
        val cardPayments = categoryId(DefaultData.cardPaymentsCategory.key)
        assertEquals(DefaultData.cardPaymentKeywords.size, rules.count { it.categoryId == cardPayments })
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
        assertTrue(rules.any { it.categoryId == categoryId(DefaultData.cardPaymentsCategory.key) })
        assertEquals(typesBefore, catalog.accountTypes.first().size) // v1 data not duplicated
    }

    @Test
    fun transfersAndCardPaymentsAreSeededOutOfSpending() = runBlocking {
        val categories = catalog.expenseCategories.first()
        val notCounted = listOf("Investments & transfers", "Credit card payments")
        assertEquals(notCounted, categories.filterNot { it.countsAsSpending }.map { it.name })
        assertTrue(categories.filter { it.name !in notCounted }.all { it.countsAsSpending })
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
        assertEquals(ExpenseCategory(categoryId(DefaultData.transfersCategory.key), "Investments & transfers", countsAsSpending = false, seedKey = DefaultData.transfersCategory.key), categories.last())
        assertTrue(categories.any { it.name == "Food" })
    }

    @Test
    fun upgradingFromVersion5TurnsOnlyUntouchedCardRulesIntoTheNewCategory() = runBlocking {
        // An install from before: the card bill rules said "don't import".
        db.backupDao().clearCategoryRules()
        DefaultData.cardPaymentKeywords.forEach { expenses.saveRule(null, it, null) }
        catalog.deleteExpenseCategory(categoryId(DefaultData.cardPaymentsCategory.key))
        val shopping = categoryId("shopping")
        expenses.saveRule(expenses.rules().single { it.keyword == "UBS CARD CENTER" }.id, "UBS CARD CENTER", shopping) // user's choice
        expenses.deleteRule(expenses.rules().single { it.keyword == "KREDITKARTENABRECHNUNG" }.id) // user deleted it
        expenses.saveRule(null, "MY BROKER", null) // the user's own "don't import" rule
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "5"))

        assertFalse(DatabaseSeeder(db, context).seedIfNeeded())

        val cardPayments = catalog.expenseCategories.first().single { it.name == "Credit card payments" }
        assertFalse(cardPayments.countsAsSpending)
        val rules = expenses.rules().associateBy { it.keyword }
        assertEquals(cardPayments.id, rules.getValue("CREDIT CARD STATEMENT").categoryId)
        assertEquals(cardPayments.id, rules.getValue("SWISSCARD AECS").categoryId)
        assertEquals(shopping, rules.getValue("UBS CARD CENTER").categoryId) // kept
        assertFalse("KREDITKARTENABRECHNUNG" in rules) // not brought back
        // The user's own "don't import" rule: since version 9 rules only categorize, so it
        // files those lines under a category that isn't spending, where they can be seen.
        assertEquals(categoryId(DefaultData.transfersCategory.key), rules.getValue("MY BROKER").categoryId)
    }

    @Test
    fun upgradingFromVersion8PointsRulesWithoutACategoryToTransfers() = runBlocking {
        val shopping = categoryId("shopping")
        expenses.saveRule(null, "MY BROKER", null) // "don't import", as before version 9
        expenses.saveRule(null, "MY SHOP", shopping)
        catalog.deleteExpenseCategory(categoryId(DefaultData.transfersCategory.key)) // deleted by the user
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "8"))

        DatabaseSeeder(db, context).seedIfNeeded()

        // Brought back, since a rule needs it, and still not counted as spending.
        val transfers = catalog.expenseCategories.first().single { it.name == "Investments & transfers" }
        assertFalse(transfers.countsAsSpending)
        val rules = expenses.rules().associateBy { it.keyword }
        assertEquals(transfers.id, rules.getValue("MY BROKER").categoryId)
        assertEquals(shopping, rules.getValue("MY SHOP").categoryId) // untouched
        assertTrue(expenses.rules().none { it.categoryId == null })
    }

    @Test
    fun upgradingFromVersion8WithoutSuchRulesChangesNothing() = runBlocking {
        catalog.deleteExpenseCategory(categoryId(DefaultData.transfersCategory.key))
        val before = expenses.rules()
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "8"))
        DatabaseSeeder(db, context).seedIfNeeded()
        assertTrue(catalog.expenseCategories.first().none { it.name == "Investments & transfers" }) // not brought back
        assertEquals(before, expenses.rules())
    }

    @Test
    fun incomeCategoriesAndSalaryRulesAreSeeded() = runBlocking {
        val income = catalog.expenseCategories.first().filter { it.isIncome }
        assertEquals(listOf("Salary", "Interest & dividends", "Other income"), income.map { it.name })
        val salary = categoryId("salary")
        assertEquals(DefaultData.salaryKeywords.toSet(), expenses.rules().filter { it.categoryId == salary }.map { it.keyword }.toSet())
    }

    @Test
    fun upgradingFromVersion6AddsIncomeOnceAndKeepsTheUsersKeywords() = runBlocking {
        DefaultData.incomeCategories.forEach { catalog.deleteExpenseCategory(categoryId(it.key)) } // as before version 7
        val other = categoryId("other")
        expenses.saveRule(null, "LOHN", other) // the user's own rule for that keyword
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "6"))

        assertFalse(DatabaseSeeder(db, context).seedIfNeeded())
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "6"))
        DatabaseSeeder(db, context).seedIfNeeded() // again: nothing twice

        val categories = catalog.expenseCategories.first()
        assertEquals(3, categories.count { it.isIncome })
        assertEquals(listOf("Salary", "Interest & dividends", "Other income"), categories.takeLast(3).map { it.name })
        val rules = expenses.rules().associateBy { it.keyword }
        assertEquals(other, rules.getValue("LOHN").categoryId) // kept
        assertEquals(categoryId("salary"), rules.getValue("SALARY").categoryId)
    }

    @Test
    fun loanAndMortgageAreLoanTypes() = runBlocking {
        val types = catalog.accountTypes.first()
        assertEquals(setOf("loan", "mortgage"), types.filter { it.isLoan }.mapNotNull { it.seedKey }.toSet())
    }

    @Test
    fun upgradingFromVersion7MarksTheSeededLoanTypesEvenIfRenamed() = runBlocking {
        val mortgage = catalog.accountTypes.first().single { it.seedKey == "mortgage" }
        db.accountTypeDao().update(db.accountTypeDao().get(mortgage.id)!!.copy(name = "Home loan", isLoan = false))
        db.accountTypeDao().update(db.accountTypeDao().get(catalog.accountTypes.first().single { it.seedKey == "loan" }.id)!!.copy(isLoan = false))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "7"))
        DatabaseSeeder(db, context).seedIfNeeded()
        val types = catalog.accountTypes.first()
        assertTrue(types.single { it.id == mortgage.id }.let { it.isLoan && it.name == "Home loan" })
        assertTrue(types.single { it.seedKey == "loan" }.isLoan)
        assertTrue(types.single { it.seedKey == "credit_card" }.isLoan.not())
    }

    @Test
    fun rulesForDeletedDefaultCategoriesAreSkipped() = runBlocking {
        db.backupDao().clearCategoryRules()
        catalog.deleteExpenseCategory(categoryId("groceries"))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "1"))
        DatabaseSeeder(db, context).seedIfNeeded()
        assertTrue(expenses.rules().none { it.keyword == "MIGROS" })
    }

    @Test
    fun upgradingFromVersion9MarksTheSeededPensionTypesEvenIfRenamed() = runBlocking {
        val pillar2 = catalog.accountTypes.first().single { it.seedKey == "ch_pillar2" }
        db.accountTypeDao().observeAll().first().filter { it.growsWithContributions }.forEach { db.accountTypeDao().update(it.copy(growsWithContributions = false)) }
        db.accountTypeDao().update(db.accountTypeDao().get(pillar2.id)!!.copy(name = "BVG"))
        db.settingsDao().put(SettingEntity(SettingKeys.SEED_VERSION, "9"))
        DatabaseSeeder(db, context).seedIfNeeded()
        val types = catalog.accountTypes.first()
        assertTrue(types.single { it.id == pillar2.id }.let { it.growsWithContributions && it.name == "BVG" })
        assertEquals(setOf("ch_pillar2", "in_epf", "in_ppf"), types.filter { it.growsWithContributions }.mapNotNull { it.seedKey }.toSet())
    }
}
