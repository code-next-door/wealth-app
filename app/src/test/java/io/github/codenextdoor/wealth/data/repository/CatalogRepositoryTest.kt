package io.github.codenextdoor.wealth.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest : DatabaseTest() {

    @Test
    fun addRenameDeleteCountry() = runBlocking {
        catalog.addCountry("Germany")
        val germany = catalog.countries.first().single { it.name == "Germany" }
        catalog.renameCountry(germany.id, "Deutschland")
        assertTrue(catalog.countries.first().any { it.name == "Deutschland" })
        catalog.deleteCountry(germany.id)
        assertTrue(catalog.countries.first().none { it.id == germany.id })
    }

    @Test
    fun deletingACountryMovesItsTypesAndAccountsToGeneral() = runBlocking {
        val accountId = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        catalog.deleteCountry(countryId("in"))
        assertNull(catalog.accountTypes.first().single { it.id == typeId("in_nre") }.countryId)
        assertNull(accounts.get(accountId)!!.countryId)
    }

    @Test
    fun addAndUpdateAccountType() = runBlocking {
        catalog.addAccountType("Crypto", AssetKind.ASSET, null)
        val crypto = catalog.accountTypes.first().single { it.name == "Crypto" }
        catalog.updateAccountType(crypto.id, "Crypto wallet", AssetKind.LIABILITY, countryId("ch"))
        val updated = catalog.accountTypes.first().single { it.id == crypto.id }
        assertEquals("Crypto wallet", updated.name)
        assertEquals(AssetKind.LIABILITY, updated.kind)
        assertEquals(countryId("ch"), updated.countryId)
    }

    @Test
    fun accountTypeInUseCantBeDeleted() = runBlocking {
        addAccount("Salary", typeSeedKey = "ch_bank")
        assertFalse(catalog.deleteAccountType(typeId("ch_bank")))
        assertTrue(catalog.deleteAccountType(typeId("gold")))
    }

    @Test
    fun deletingACategoryUncategorizesExpensesAndRemovesItsRules() = runBlocking {
        val groceries = categoryId("groceries")
        expenses.save(expense("COOP", 10_00, categoryId = groceries))
        catalog.deleteExpenseCategory(groceries)
        assertNull(expenses.expensesBetween(today, today).first().single().categoryId)
        assertTrue(expenses.rules().none { it.categoryId == groceries })
    }

    @Test
    fun renameCategory() = runBlocking {
        catalog.renameExpenseCategory(categoryId("groceries"), "Food shopping")
        assertTrue(catalog.expenseCategories.first().any { it.name == "Food shopping" })
    }

    @Test
    fun categoryCanBeLeftOutOfSpending() = runBlocking {
        val groceries = categoryId("groceries")
        catalog.setCountsAsSpending(groceries, false)
        assertFalse(catalog.expenseCategories.first().single { it.id == groceries }.countsAsSpending)
        catalog.renameExpenseCategory(groceries, "Food") // renaming keeps the choice
        assertFalse(catalog.expenseCategories.first().single { it.id == groceries }.countsAsSpending)
        catalog.setCountsAsSpending(groceries, true)
        assertTrue(catalog.expenseCategories.first().single { it.id == groceries }.countsAsSpending)
    }

    @Test
    fun categoriesCanBeReordered() = runBlocking {
        val before = catalog.expenseCategories.first().map { it.id }
        val reordered = listOf(before.last()) + before.dropLast(1) // the last one to the top
        catalog.reorderExpenseCategories(reordered)
        assertEquals(reordered, catalog.expenseCategories.first().map { it.id })

        catalog.addExpenseCategory("Pets") // a new one still goes last
        assertEquals("Pets", catalog.expenseCategories.first().last().name)
    }

    @Test
    fun newCategoriesCountAsSpending() = runBlocking {
        catalog.addExpenseCategory("Pets")
        assertTrue(catalog.expenseCategories.first().single { it.name == "Pets" }.countsAsSpending)
    }

    @Test
    fun accountTypesCanHoldShares() = runBlocking {
        catalog.addAccountType("Brokerage (shares)", AssetKind.ASSET, null, holdsShares = true)
        val type = catalog.accountTypes.first().single { it.name == "Brokerage (shares)" }
        assertTrue(type.holdsShares)
        catalog.updateAccountType(type.id, "Brokerage", AssetKind.ASSET, null) // leaves the setting alone
        assertTrue(catalog.accountTypes.first().single { it.id == type.id }.holdsShares)
        catalog.updateAccountType(type.id, "Brokerage", AssetKind.ASSET, null, holdsShares = false)
        assertFalse(catalog.accountTypes.first().single { it.id == type.id }.holdsShares)
    }
}
