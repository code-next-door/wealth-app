package io.github.codenextdoor.wealth.widget

import io.github.codenextdoor.wealth.dashboard.Delta
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.BalanceEntry
import io.github.codenextdoor.wealth.domain.CashFlow
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.LoanInterest
import io.github.codenextdoor.wealth.domain.NetWorthAccounts
import io.github.codenextdoor.wealth.domain.NetWorthCalculator
import io.github.codenextdoor.wealth.domain.Pension
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.Property
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.formatPercent
import io.github.codenextdoor.wealth.domain.maskFigures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the home-screen widget shows: net worth, its change this month (since the end of
 * last month) and this month's spending, figured like the Overview and Spending tab.
 * [hidden]: the eye is closed or the app lock is on; then every figure is already masked
 * here, so real amounts never reach the home screen.
 */
data class WidgetData(
    val hasAccounts: Boolean,
    val hidden: Boolean,
    val netWorthText: String,
    val change: Delta?,
    val month: YearMonth,
    val spentText: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetDataSource(
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    shareRepository: ShareRepository,
    houseRepository: HouseRepository,
    expenseRepository: ExpenseRepository,
    loans: Flow<List<Loan>>,
    pensions: Flow<List<Pension>>,
    today: StateFlow<LocalDate>,
    figuresHidden: Flow<Boolean>,
    appLockOn: Flow<Boolean>,
) {
    private data class Data(val accounts: List<Account>, val entries: List<BalanceEntry>, val types: List<AccountType>, val categories: List<ExpenseCategory>)

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook, val prices: PriceBook)

    private data class Houses(val houses: List<Property>, val counted: Boolean, val loans: List<Loan>, val pensions: List<Pension>)

    private val monthExpenses = today.flatMapLatest { day ->
        val month = YearMonth.from(day)
        expenseRepository.expensesBetween(month.atDay(1), month.atEndOfMonth()).map { day to it }
    }

    val data: Flow<WidgetData> = combine(
        combine(accountRepository.accounts, accountRepository.balanceEntries, catalogRepository.accountTypes, catalogRepository.expenseCategories, ::Data),
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, shareRepository.prices, ::Money),
        combine(houseRepository.houses, houseRepository.inNetWorth, loans, pensions, ::Houses),
        monthExpenses,
        combine(figuresHidden, appLockOn) { eye, lock -> eye || lock },
    ) { (accounts, entries, types, categories), money, houses, (day, expenses), hidden ->

        val decimals = money.currencies.associate { it.code to it.decimals }
        val dec = decimals[money.base] ?: 2
        val split = NetWorthAccounts.split(accounts, entries, types.associateBy { it.id }, decimals, houses.houses, houses.counted, houses.loans, houses.pensions)
        val calc = NetWorthCalculator(split.counted, money.rates, money.base, money.prices)
        val month = YearMonth.from(day)
        val now = calc.netWorthAt(day)
        val monthStart = calc.netWorthAt(month.minusMonths(1).atEndOfMonth())
        val change = now - monthStart
        val spent = CashFlow.of(
            expenses, categories.associateBy { it.id }, money.rates, money.base, { decimals[it] ?: 2 },
            LoanInterest.lookup(houses.loans, entries, accounts),
        ).spendingSummary.total

        fun show(text: String) = if (hidden) maskFigures(text) else text
        WidgetData(
            hasAccounts = accounts.isNotEmpty(),
            hidden = hidden,
            netWorthText = show(formatMoney(now, money.base, dec)),
            change = if (calc.earliestDate == null || hidden) {
                null
            } else {
                Delta(
                    amountText = (if (change.signum() < 0) "−" else "+") + formatMoney(change.abs(), money.base, dec),
                    percentText = monthStart.takeIf { it.signum() != 0 }?.let {
                        val pct = change.multiply(BigDecimal(100)).divide(it.abs(), 1, RoundingMode.HALF_EVEN)
                        (if (pct.signum() < 0) "−" else "+") + formatPercent(pct.abs())
                    },
                    isIncrease = change.signum() >= 0,
                )
            },
            month = month,
            spentText = show(formatMoney(spent, money.base, dec)),
        )
    }
}
