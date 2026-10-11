package io.github.codenextdoor.wealth.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** What the home-screen widget shows, and when it hides the figures. */
@RunWith(AndroidJUnit4::class)
class WidgetDataTest : DatabaseTest() {

    private val figuresHidden = MutableStateFlow(false)
    private val lockOn = MutableStateFlow(false)

    private fun source() = WidgetDataSource(
        accounts, catalog, currencies, ShareRepository(db), HouseRepository(db, accounts), expenses,
        loans = flowOf(emptyList()), pensions = flowOf(emptyList()), today = todayFlow,
        figuresHidden = figuresHidden, appLockOn = lockOn,
    )

    private val thisMonth get() = YearMonth.from(today)

    @Test
    fun netWorthItsChangeThisMonthAndSpending() {
        val savings = addAccount("Savings", balanceMinor = 10_000_00, date = thisMonth.minusMonths(1).atEndOfMonth())
        runBlocking {
            accounts.addHistoryEntry(savings, today, 12_340_00)
            expenses.save(expense("RENT", 1_820_00, date = thisMonth.atDay(1)))
        }
        val data = source().data.await { it.hasAccounts }
        assertFalse(data.hidden)
        assertTrue(data.netWorthText.withPlainSpaces(), data.netWorthText.withPlainSpaces().endsWith("12’340.00"))
        assertTrue(data.change!!.isIncrease)
        assertTrue(data.change.amountText.withPlainSpaces().endsWith("2’340.00"))
        assertTrue(data.spentText.withPlainSpaces().endsWith("1’820.00"))
        assertEquals(thisMonth, data.month)
    }

    @Test
    fun theEyeOrTheAppLockHideEveryFigure() {
        addAccount("Savings", balanceMinor = 10_000_00, date = today)
        val source = source()
        source.data.await { it.hasAccounts && !it.hidden }

        figuresHidden.value = true
        val masked = source.data.await { it.hidden }
        assertFalse(masked.netWorthText.any { it.isDigit() })
        assertFalse(masked.spentText.any { it.isDigit() })

        figuresHidden.value = false
        source.data.await { !it.hidden }
        lockOn.value = true // the widget must not show what the lock protects
        val locked = source.data.await { it.hidden }
        assertFalse(locked.netWorthText.any { it.isDigit() })
    }

    @Test
    fun noAccountsYet() {
        assertFalse(source().data.await { true }.hasAccounts)
    }
}
