package io.github.codenextdoor.wealth.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.ExpensePart
import io.github.codenextdoor.wealth.settings.CategoriesViewModel
import io.github.codenextdoor.wealth.settings.CategoryDetailViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Settings › Categories and patterns: each category's patterns, and changes applying to saved transactions. */
@RunWith(AndroidJUnit4::class)
class CategoryPatternsTest : DatabaseTest() {

    private fun detail(id: Long) = CategoryDetailViewModel(id, catalog, expenses).cancelledAfterTest()
        .also { vm -> vm.state.await { it.category != null } }

    private fun saved(description: String) = runBlocking {
        expenses.expensesBetween(today.minusYears(1), today).first().single { it.description == description }
    }

    @Test
    fun aCategoryListsItsPatternsAToZ() {
        val vm = detail(categoryId("groceries"))
        val keywords = vm.state.value.patterns.map { it.keyword }
        assertTrue(keywords.containsAll(listOf("COOP", "MIGROS")))
        assertEquals(keywords.sorted(), keywords)
    }

    @Test
    fun addingAPatternRecategorizesSavedTransactionsButNotHandPickedOrSplitOnes() {
        val shopping = categoryId("shopping")
        runBlocking {
            expenses.save(expense("ACME STORE ZURICH", 10_00)) // uncategorized, no rule knows it
            expenses.save(expense("ACME STORE BERN", 20_00, categoryId = categoryId("other"), locked = true)) // picked by hand
            expenses.save(expense("ACME STORE BASEL", 30_00).copy(parts = listOf(ExpensePart(amountMinor = 5_00, categoryId = null)))) // split
        }
        val vm = detail(shopping)
        vm.addPattern("acme store")
        assertEquals(1, vm.state.await { it.reapplied != null }.reapplied)
        assertEquals(shopping, saved("ACME STORE ZURICH").categoryId)
        assertEquals(categoryId("other"), saved("ACME STORE BERN").categoryId)
        assertNull(saved("ACME STORE BASEL").categoryId)
        assertTrue(vm.state.value.patterns.any { it.keyword == "ACME STORE" })
    }

    @Test
    fun editingMovingAndDeletingAPatternApplyToo() {
        val shopping = categoryId("shopping")
        val vm = detail(shopping)
        runBlocking { expenses.save(expense("ACME STORE ZURICH", 10_00)) }
        vm.addPattern("ACME")
        vm.state.await { it.reapplied != null }
        vm.reappliedShown()
        assertEquals(shopping, saved("ACME STORE ZURICH").categoryId)

        // Edited to a keyword that no longer matches: back to uncategorized.
        val acme = vm.state.value.patterns.single { it.keyword == "ACME" }
        vm.savePattern(acme.id, "ACMEX", shopping)
        vm.state.await { it.reapplied != null }
        vm.reappliedShown()
        assertNull(saved("ACME STORE ZURICH").categoryId)

        // Moved to another category: it leaves this list.
        vm.addPattern("ACME")
        vm.state.await { it.reapplied != null }
        vm.reappliedShown()
        val moved = vm.state.value.patterns.single { it.keyword == "ACME" }
        vm.savePattern(moved.id, "ACME", categoryId("gifts"))
        vm.state.await { s -> s.patterns.none { it.keyword == "ACME" } }
        assertEquals(categoryId("gifts"), saved("ACME STORE ZURICH").categoryId)

        // Deleted (from its new category).
        val gifts = detail(categoryId("gifts"))
        gifts.deletePattern(gifts.state.value.patterns.single { it.keyword == "ACME" }.id)
        gifts.state.await { it.reapplied != null }
        assertNull(saved("ACME STORE ZURICH").categoryId)
    }

    @Test
    fun aKeywordFromAnotherCategorySaysWhereItIsAndMovesHere() {
        val shopping = categoryId("shopping")
        val vm = detail(shopping)
        assertEquals("Groceries", vm.ownerElsewhere("coop")?.name)
        assertNull(vm.ownerElsewhere("SOMETHING NEW"))
        vm.addPattern("COOP")
        vm.state.await { s -> s.patterns.any { it.keyword == "COOP" } }
        assertTrue(runBlocking { expenses.rules() }.single { it.keyword == "COOP" }.categoryId == shopping)
    }

    @Test
    fun deletingACategoryUncategorizesItsTransactionsAndRemovesItsPatterns() {
        val gifts = categoryId("gifts")
        runBlocking {
            expenses.saveRule(null, "FLOWER SHOP", gifts)
            expenses.save(expense("FLOWER SHOP LUZERN", 40_00, categoryId = gifts))
        }
        val vm = detail(gifts)
        vm.delete()
        vm.state.await { it.deleted }
        assertNull(saved("FLOWER SHOP LUZERN").categoryId)
        assertTrue(runBlocking { expenses.rules() }.none { it.keyword == "FLOWER SHOP" })
        assertTrue(runBlocking { catalog.expenseCategories.first() }.none { it.id == gifts })
    }

    @Test
    fun renamingAndTurningIntoIncomeApply() {
        val other = categoryId("other")
        runBlocking {
            expenses.saveRule(null, "REFUNDCO", other)
            expenses.save(expense("REFUNDCO PAYMENT", 15_00)) // money out
        }
        val vm = detail(other)
        vm.rename("Misc", income = false)
        vm.state.await { it.category?.name == "Misc" }
        // As income, its patterns only match money in: the money-out row loses it.
        runBlocking { expenses.reapplyRules() }
        assertEquals(other, saved("REFUNDCO PAYMENT").categoryId)
        vm.rename("Misc", income = true)
        vm.state.await { it.category?.isIncome == true && it.reapplied != null }
        assertNull(saved("REFUNDCO PAYMENT").categoryId)
    }

    @Test
    fun theListCountsPatternsAndTestsAStatementLine() {
        val vm = CategoriesViewModel(catalog, expenses).cancelledAfterTest()
        val counts = vm.patternCounts.await { it.isNotEmpty() }
        assertTrue(counts.getValue(categoryId("groceries")) >= 2)
        assertEquals(null, counts[categoryId("gifts")]?.takeIf { it < 0 })

        vm.testField.setTextAndPlaceCursorAtEnd("TWINT *COOP-4521 ZUERICH")
        val match = vm.testMatch(vm.lists.await { it.rules.isNotEmpty() })!!
        assertEquals("COOP", match.keyword)
        assertEquals(categoryId("groceries"), match.categoryId)
        assertEquals("Groceries", match.categoryName)
        assertTrue(match.countsAsSpending)
        // Paying the card isn't spending: the test line says so.
        vm.testField.setTextAndPlaceCursorAtEnd("C/O UBS CARD CENTER")
        val card = vm.testMatch(vm.lists.value)!!
        assertEquals(categoryId("card_payments"), card.categoryId)
        assertFalse(card.countsAsSpending)
        vm.testField.setTextAndPlaceCursorAtEnd("NOTHING KNOWN HERE")
        assertNull(vm.testMatch(vm.lists.value))
        assertFalse(vm.lists.value.rules.isEmpty())
    }
}
