package io.github.codenextdoor.wealth.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.review.MonthlyReviewViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** The monthly review screen: last month by default, arrows within the months with data. */
@RunWith(AndroidJUnit4::class)
class MonthlyReviewViewModelTest : DatabaseTest() {

    private val lastMonth = YearMonth.from(today).minusMonths(1)

    private fun viewModel(month: YearMonth? = null) = MonthlyReviewViewModel(
        month, accounts, catalog, currencies, ShareRepository(db), HouseRepository(db, accounts), expenses,
        loans = flowOf(emptyList()), pensions = flowOf(emptyList()), calculated = flowOf(emptySet()), today = todayFlow,
    ).cancelledAfterTest()

    @Test
    fun lastMonthByDefaultWithItsNetWorthChange() {
        val savings = addAccount("Savings", balanceMinor = 10_000_00, date = lastMonth.minusMonths(1).atEndOfMonth())
        runBlocking { accounts.addHistoryEntry(savings, lastMonth.atEndOfMonth(), 11_000_00, null) }
        val state = viewModel().state.await { !it.isLoading }
        assertEquals(lastMonth, state.month)
        assertTrue(state.change!!.isIncrease)
        assertTrue(state.change.amountText.withPlainSpaces(), state.change.amountText.withPlainSpaces().endsWith("1’000.00"))
        assertEquals(listOf("Savings"), state.movers.map { it.name })
    }

    @Test
    fun incomeSpendingAndCategories() {
        addAccount("Savings", date = lastMonth.minusMonths(2).atEndOfMonth())
        runBlocking {
            expenses.save(expense("SALARY", -5_000_00, date = lastMonth.atDay(25), categoryId = categoryId("salary")))
            expenses.save(expense("RENT", 2_000_00, date = lastMonth.atDay(1), categoryId = categoryId("housing")))
        }
        val state = viewModel().state.await { !it.isLoading }
        assertTrue(state.spendingText.withPlainSpaces().endsWith("2’000.00"))
        assertTrue(state.savedText.withPlainSpaces().endsWith("3’000.00"))
        assertEquals("60.0%", state.savingsRateText)
        assertEquals(1, state.categories.size)
    }

    @Test
    fun arrowsStayWithinTheMonthsWithData() {
        addAccount("Savings", date = lastMonth.minusMonths(2).atDay(10))
        val vm = viewModel()
        val first = vm.state.await { !it.isLoading }
        assertFalse(first.canGoForward) // last month is the newest finished one
        assertTrue(first.canGoBack)
        vm.previous()
        vm.previous()
        val oldest = vm.state.await { it.month == lastMonth.minusMonths(2) }
        assertFalse(oldest.canGoBack)
        vm.next()
        assertEquals(lastMonth.minusMonths(1), vm.state.await { it.month == lastMonth.minusMonths(1) }.month)
    }

    @Test
    fun anAccountWithoutABalanceInTheMonthIsListed() {
        addAccount("Old pillar 3a", date = lastMonth.minusMonths(3).atDay(1))
        val state = viewModel().state.await { !it.isLoading }
        assertEquals(listOf("Old pillar 3a"), state.notUpdated.map { it.second })
    }
}
