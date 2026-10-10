package io.github.codenextdoor.wealth.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.expenses.SearchViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spending › Search: every transaction, any month. */
@RunWith(AndroidJUnit4::class)
class SearchViewModelTest : DatabaseTest() {

    private fun search(text: String? = null, category: Long? = null) =
        SearchViewModel(expenses, catalog, accounts, currencies, todayFlow, text, category).cancelledAfterTest()

    /** Types like the screen does; in the app Compose announces the change, here the test does. */
    private fun SearchViewModel.type(text: String) {
        query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun nothingTypedShowsNothing() {
        runBlocking { expenses.save(expense("COOP ZURICH", 10_00)) }
        val vm = search()
        val state = vm.state.await { !it.isLoading }
        assertFalse(state.searched)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun findsMatchesInEveryMonthNewestFirstWithTheirNames() {
        val card = addAccount("Everyday card")
        runBlocking {
            expenses.save(expense("COOP ZURICH", 10_00, date = today.minusMonths(14), categoryId = categoryId("groceries"), accountId = card))
            expenses.save(expense("COOP BERN", 20_00, date = today.minusDays(3)))
            expenses.save(expense("MIGROS", 30_00, date = today))
        }
        val vm = search()
        vm.type("coop")
        val state = vm.state.await { it.searched && it.count == 2 }
        assertEquals(listOf("COOP BERN", "COOP ZURICH"), state.results.map { it.row.description })
        assertEquals(listOf(today.minusDays(3), today.minusMonths(14)), state.results.map { it.date })
        val older = state.results.last().row
        assertEquals("Groceries", older.categoryName)
        assertEquals("Everyday card", older.accountName)
    }

    @Test
    fun showsTotalsPerCurrencyOutAndIn() {
        runBlocking {
            expenses.save(expense("SHOP A", 10_00))
            expenses.save(expense("SHOP B", 5_50))
            expenses.save(expense("SHOP REFUND", -2_00))
        }
        val vm = search()
        vm.type("shop")
        val total = vm.state.await { it.count == 3 }.totals.single()
        assertTrue(total.outText.withPlainSpaces(), total.outText.withPlainSpaces().endsWith("15.50"))
        assertTrue(total.inText!!.withPlainSpaces().endsWith("2.00"))
    }

    @Test
    fun aNewTransactionShowsUpWhileSearching() {
        val vm = search()
        vm.type("bakery")
        vm.state.await { it.searched && it.count == 0 }
        runBlocking { expenses.save(expense("BAKERY", 4_00)) }
        assertEquals("BAKERY", vm.state.await { it.count == 1 }.results.single().row.description)
    }

    @Test
    fun longListsShowTheNewestButCountEverything() {
        runBlocking { repeat(SearchViewModel.MAX_ROWS + 5) { expenses.save(expense("TRAIN $it", 1_00, date = today.minusDays(it.toLong()))) } }
        val vm = search()
        vm.type("train")
        val state = vm.state.await { it.count == SearchViewModel.MAX_ROWS + 5 }
        assertEquals(SearchViewModel.MAX_ROWS, state.results.size)
        assertEquals("TRAIN 0", state.results.first().row.description)
    }

    @Test
    fun openedForACategoryShowsItsTransactionsAndTwelveMonths() {
        val groceries = categoryId("groceries")
        runBlocking {
            expenses.save(expense("COOP", 40_00, date = today, categoryId = groceries))
            expenses.save(expense("MIGROS", 10_00, date = today.minusMonths(2), categoryId = groceries))
            expenses.save(expense("SBB", 99_00, date = today))
        }
        val state = search(category = groceries).state.await { it.count == 2 }
        assertTrue(state.searched)
        assertEquals(groceries, state.categoryFilter)
        val bars = state.chart!!.bars
        assertEquals(12, bars.size)
        assertEquals(java.time.YearMonth.from(today), bars.last().month)
        assertEquals(1f, bars.last().fraction) // the biggest month
        assertEquals(0.25f, bars[9].fraction)
        assertTrue(bars.last().amountText.withPlainSpaces().endsWith("40.00"))
        assertFalse(state.chart!!.moneyIn)
    }

    @Test
    fun aMonthShowsOnlyItsRowsAndTotalsButTheChartStays() {
        runBlocking {
            expenses.save(expense("COOP", 40_00, date = today))
            expenses.save(expense("COOP", 10_00, date = today.minusMonths(1)))
        }
        val vm = search(text = "coop")
        assertEquals("coop", vm.query.text.toString())
        vm.state.await { it.count == 2 }
        val month = java.time.YearMonth.from(today.minusMonths(1))
        vm.selectMonth(month)
        val state = vm.state.await { it.month == month }
        assertEquals(1, state.count)
        assertTrue(state.totals.single().outText.withPlainSpaces().endsWith("10.00"))
        assertEquals(12, state.chart!!.bars.size)
        vm.selectMonth(month) // again: all months
        assertEquals(2, vm.state.await { it.month == null }.count)
    }

    @Test
    fun categoryAndAccountFiltersCombineWithText() {
        val card = addAccount("Everyday card")
        val groceries = categoryId("groceries")
        runBlocking {
            expenses.save(expense("COOP", 40_00, categoryId = groceries, accountId = card))
            expenses.save(expense("COOP", 10_00, categoryId = groceries))
            expenses.save(expense("COOP BAR", 5_00, accountId = card))
        }
        val vm = search()
        vm.type("coop")
        vm.state.await { it.count == 3 }
        vm.setCategory(groceries)
        assertEquals(2, vm.state.await { it.categoryFilter == groceries }.count)
        vm.setAccount(card)
        assertEquals(1, vm.state.await { it.accountFilter == card }.count)
        vm.setCategory(io.github.codenextdoor.wealth.expenses.UNCATEGORIZED)
        assertEquals(listOf("COOP BAR"), vm.state.await { it.categoryFilter == io.github.codenextdoor.wealth.expenses.UNCATEGORIZED }.results.map { it.row.description })
        // The filter choices list the accounts and categories.
        assertTrue(vm.state.value.accounts.any { it.name == "Everyday card" })
        assertTrue(vm.state.value.categories.any { it.name == "Groceries" })
    }

    @Test
    fun anIncomeCategoryChartsMoneyIn() {
        val salary = categoryId("salary")
        runBlocking { expenses.save(expense("EMPLOYER AG", -8_000_00, categoryId = salary)) }
        val chart = search(category = salary).state.await { it.count == 1 }.chart!!
        assertTrue(chart.moneyIn)
        assertEquals(1f, chart.bars.last().fraction)
    }
}
