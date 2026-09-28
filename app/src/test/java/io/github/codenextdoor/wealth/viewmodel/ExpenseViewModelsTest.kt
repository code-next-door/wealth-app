package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.ui.Routes
import androidx.navigation.testing.invoke
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.expenses.ExpenseEditViewModel
import io.github.codenextdoor.wealth.expenses.ExpensesViewModel
import io.github.codenextdoor.wealth.expenses.RulesViewModel
import io.github.codenextdoor.wealth.expenses.UNCATEGORIZED
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class ExpenseViewModelsTest : DatabaseTest() {

    private fun editor(id: Long? = null) = ExpenseEditViewModel(
        SavedStateHandle(route = Routes.ExpenseEdit(id)),
        expenses, catalog, accounts, currencies,
    ).cancelledAfterTest().also { vm -> vm.data.await { vm.uiState(it).isReady && vm.uiState(it).categories.isNotEmpty() } }

    private fun digits(text: String) = text.filter { it.isDigit() }

    @Test
    fun categoryFollowsRulesWhileTyping() {
        val vm = editor()
        vm.onDescriptionChange("TWINT *COOP-4521 ZUERICH")
        assertEquals(categoryId("groceries"), vm.uiState().form.categoryId)
        assertEquals("COOP", vm.uiState().matchedRule!!.keyword)
        vm.onDescriptionChange("UBER EATS ZURICH")
        assertEquals(categoryId("eating_out"), vm.uiState().form.categoryId)
    }

    @Test
    fun savingValidatesAndStoresTheRuleCategoryUnlocked() {
        val vm = editor()
        vm.save()
        assertTrue(vm.uiState().descriptionError && vm.uiState().amountError)
        vm.onDescriptionChange("MIGROS BERN")
        vm.onAmountChange("12.50")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        assertEquals(12_50L, saved.amountMinor)
        assertEquals(categoryId("groceries"), saved.categoryId)
        assertFalse(saved.categoryLocked)
    }

    @Test
    fun pickingAnAccountPicksItsCurrency() {
        val nre = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor()
        vm.onAccountChange(nre)
        assertEquals("INR", vm.uiState().form.currencyCode)
    }

    @Test
    fun correctingACategoryOffersARuleAndAcceptingAppliesIt() {
        expenses.let { runBlocking { it.save(expense("BAECKEREI HUG LUZERN", 5_00)) } } // an older one, uncategorized
        val vm = editor()
        vm.onDescriptionChange("TWINT *BAECKEREI HUG")
        vm.onAmountChange("4.20")
        vm.onCategoryChange(categoryId("eating_out"))
        vm.save()
        val suggestion = vm.data.await { vm.uiState(it).ruleSuggestion != null }.let { vm.uiState(it).ruleSuggestion!! }
        assertEquals("BAECKEREI", suggestion.keyword) // TWINT skipped

        vm.acceptRule(suggestion.keyword)
        vm.data.await { vm.uiState(it).isFinished }
        val all = runBlocking { expenses.expensesBetween(today, today).first() }
        assertTrue(all.all { it.categoryId == categoryId("eating_out") }) // the older one too
        assertTrue(all.single { it.amountMinor == 4_20L }.categoryLocked)
    }

    @Test
    fun noSuggestionWhenTheRuleAlreadyAgrees() {
        val vm = editor()
        vm.onDescriptionChange("MIGROS")
        vm.onAmountChange("1")
        vm.onCategoryChange(categoryId("groceries"))
        vm.save()
        assertNull(vm.data.await { vm.uiState(it).isFinished }.let { vm.uiState(it).ruleSuggestion })
    }

    @Test
    fun editingAndDeletingAnExistingExpense() {
        runBlocking { expenses.save(expense("Book", 20_00, categoryId = categoryId("education"), locked = true)) }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        val vm = editor(saved.id)
        assertEquals("Book", vm.uiState().form.description)
        assertEquals("20", vm.uiState().form.amountText)
        vm.delete()
        vm.data.await { vm.uiState(it).isFinished }
        assertNull(runBlocking { expenses.get(saved.id) })
    }

    @Test
    fun spendingSummaryFilterAndMonths() {
        setRate("CHF", "INR", "100")
        val groceries = categoryId("groceries")
        runBlocking {
            expenses.save(expense("COOP", 40_00, categoryId = groceries))
            expenses.save(expense("SWIGGY", 1_000_00, currency = "INR", categoryId = categoryId("eating_out")))
            expenses.save(expense("Mystery", 5_00))
            expenses.save(expense("Refund", -5_00, categoryId = groceries))
            expenses.save(expense("Last month", 100_00, date = today.minusMonths(1).withDayOfMonth(1), categoryId = groceries))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        assertEquals("5000", digits(state.totalText)) // 40 + 10 + 5 - 5
        assertEquals(3, state.slices.size)
        assertTrue(state.slices.any { it.key == UNCATEGORIZED })
        assertNotNull(state.vsPreviousText)
        assertFalse(state.spentMore) // 50 this month vs 100 last month

        vm.toggleFilter(groceries)
        val filtered = vm.uiState.await { it.filter == groceries }.days.flatMap { it.second }
        assertEquals(setOf("COOP", "Refund"), filtered.map { it.description }.toSet())
        assertTrue(filtered.single { it.description == "Refund" }.isRefund)
        vm.toggleFilter(groceries)
        assertNull(vm.uiState.await { it.filter == null }.filter)

        vm.previousMonth()
        val previous = vm.uiState.await { it.month == YearMonth.from(today).minusMonths(1) }
        assertEquals(listOf("Last month"), previous.days.flatMap { it.second }.map { it.description })
    }

    @Test
    fun aNewMonthMovesTheCurrentMonthOnButNotAnOlderOne() {
        val thisMonth = YearMonth.from(today)
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        assertFalse(vm.uiState.await { !it.isLoading }.canGoForward)
        vm.nextMonth() // nothing after the current month
        assertEquals(thisMonth, vm.uiState.value.month)

        todayFlow.value = thisMonth.plusMonths(1).atDay(1) // midnight on the 1st, app still open
        assertFalse(vm.uiState.await { it.month == thisMonth.plusMonths(1) }.canGoForward)

        vm.previousMonth() // looking back...
        assertTrue(vm.uiState.await { it.month == thisMonth }.canGoForward)
        todayFlow.value = thisMonth.plusMonths(2).atDay(1)
        // ...stays where it was, and can now go two months forward.
        vm.uiState.await { it.canGoForward && it.month == thisMonth }
        vm.nextMonth()
        vm.nextMonth()
        vm.nextMonth()
        assertFalse(vm.uiState.await { it.month == thisMonth.plusMonths(2) }.canGoForward)
    }

    @Test
    fun rulesScreenTestsTextAndManagesRules() {
        val vm = RulesViewModel(expenses, catalog).cancelledAfterTest()
        vm.data.await { it.let { d -> vm.uiState(d).rules.isNotEmpty() } }
        vm.onTestTextChange("UBER *TRIP")
        assertEquals("UBER", vm.uiState().testMatch!!.keyword)
        vm.onTestTextChange("C/O UBS CARD CENTER")
        assertNull(vm.uiState().testMatch!!.categoryId) // a "don't import" rule

        vm.save(null, "wise payments", null)
        vm.data.await { d -> vm.uiState(d).rules.any { it.keyword == "WISE PAYMENTS" && it.categoryId == null } }

        runBlocking { expenses.save(expense("MIGROS", 1_00)) }
        vm.reapply()
        assertEquals(1, vm.data.await { vm.uiState(it).reappliedCount != null }.let { vm.uiState(it).reappliedCount })
    }
}
