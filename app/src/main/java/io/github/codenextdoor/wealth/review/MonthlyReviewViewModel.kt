package io.github.codenextdoor.wealth.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.dashboard.Delta
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.LoanInterest
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.Property
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.domain.Expense
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.MonthlyReview
import io.github.codenextdoor.wealth.domain.NetWorthAccounts
import io.github.codenextdoor.wealth.domain.NetWorthCalculator
import io.github.codenextdoor.wealth.domain.Pension
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.domain.formatPercent
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class ReviewMover(val accountId: Long, val name: String, val change: Delta)

data class ReviewCategory(val name: String?, val amountText: String, val previousText: String, val change: Delta)

data class MonthlyReviewUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now().minusMonths(1),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasAccounts: Boolean = false,
    val startText: String = "",
    val endText: String = "",
    /** Null without any accounts. */
    val change: Delta? = null,
    val movers: List<ReviewMover> = emptyList(),
    val incomeText: String = "",
    val spendingText: String = "",
    val savedText: String = "",
    /** Null without income. */
    val savingsRateText: String? = null,
    val previousSpendingText: String = "",
    /** Spending against the month before (an increase is more spent). */
    val spendingChange: Delta? = null,
    val previousSavingsRateText: String? = null,
    val categories: List<ReviewCategory> = emptyList(),
    /** Accounts without a balance in the month: id and name. */
    val notUpdated: List<Pair<Long, String>> = emptyList(),
)

/**
 * A finished month ([MonthlyReview]): last month unless [month] is given; arrows go back
 * to the first month with data and forward to last month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReviewViewModel(
    month: YearMonth?,
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    shareRepository: ShareRepository,
    houseRepository: HouseRepository,
    expenseRepository: ExpenseRepository,
    loans: Flow<List<Loan>>,
    pensions: Flow<List<Pension>>,
    calculated: Flow<Set<Long>>,
    private val today: StateFlow<LocalDate>,
) : ViewModel() {

    private val lastFinished get() = YearMonth.from(today.value).minusMonths(1)
    private val selected = MutableStateFlow(month ?: lastFinished)

    private data class Inputs(
        val calculator: NetWorthCalculator,
        val rates: RateBook,
        val categories: Map<Long, ExpenseCategory>,
        val decimals: Map<String, Int>,
        val loanInterest: (Long, YearMonth) -> Pair<Long, String>?,
        val earliest: YearMonth?,
        val hasAccounts: Boolean,
    )

    private data class Money(val currencies: List<Currency>, val base: String, val rates: RateBook, val prices: PriceBook)

    private data class Houses(val houses: List<Property>, val counted: Boolean, val loans: List<Loan>, val pensions: List<Pension>)

    private val inputs: Flow<Inputs> = combine(
        combine(accountRepository.accounts, accountRepository.balanceEntries, catalogRepository.accountTypes) { a, e, t -> Triple(a, e, t) },
        combine(currencyRepository.currencies, currencyRepository.baseCurrency, currencyRepository.rateBook, shareRepository.prices, ::Money),
        combine(houseRepository.houses, houseRepository.inNetWorth, loans, pensions, ::Houses),
        catalogRepository.expenseCategories,
        expenseRepository.earliestDate,
    ) { (accounts, entries, types), money, houses, categories, earliestExpense ->
        val decimals = money.currencies.associate { it.code to it.decimals }
        val split = NetWorthAccounts.split(accounts, entries, types.associateBy { it.id }, decimals, houses.houses, houses.counted, houses.loans, houses.pensions)
        Inputs(
            calculator = NetWorthCalculator(split.counted, money.rates, money.base, money.prices),
            rates = money.rates,
            categories = categories.associateBy { it.id },
            decimals = decimals,
            loanInterest = LoanInterest.lookup(houses.loans, entries, accounts),
            earliest = listOfNotNull(entries.minOfOrNull { it.date }, earliestExpense).minOrNull()?.let(YearMonth::from),
            hasAccounts = accounts.isNotEmpty(),
        )
    }

    val state: StateFlow<MonthlyReviewUiState> = combine(selected, today) { m, _ -> m }
        .flatMapLatest { m ->
            // This month and the one before (for "vs last month").
            expenseRepository.expensesBetween(m.minusMonths(1).atDay(1), m.atEndOfMonth()).map { m to it }
        }
        .combine(inputs) { (m, expenses), input -> build(m, expenses, input) }
        .combine(calculated) { build, ids -> build(ids) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyReviewUiState())

    private fun build(month: YearMonth, expenses: List<Expense>, input: Inputs): (Set<Long>) -> MonthlyReviewUiState = { calculated ->
        val calc = input.calculator
        val base = calc.baseCurrency
        val dec = input.decimals[base] ?: 2
        fun money(v: BigDecimal) = formatMoney(v, base, dec)
        fun percent(v: BigDecimal) = formatPercent(v.setScale(1, RoundingMode.HALF_EVEN))
        fun delta(change: BigDecimal, from: BigDecimal?) = Delta(
            amountText = (if (change.signum() < 0) "−" else "+") + money(change.abs()),
            percentText = from?.takeIf { it.signum() != 0 }?.let {
                val pct = change.multiply(BigDecimal(100)).divide(it.abs(), 1, RoundingMode.HALF_EVEN)
                (if (pct.signum() < 0) "−" else "+") + formatPercent(pct.abs())
            },
            isIncrease = change.signum() >= 0,
        )
        val review = MonthlyReview.of(
            month, calc, expenses, input.categories, input.rates, base, { input.decimals[it] ?: 2 }, calculated, input.loanInterest,
        )
        MonthlyReviewUiState(
            isLoading = false,
            month = month,
            canGoBack = input.earliest != null && month > input.earliest,
            canGoForward = month < lastFinished,
            hasAccounts = input.hasAccounts,
            startText = money(review.startNetWorth),
            endText = money(review.endNetWorth),
            change = if (input.hasAccounts) delta(review.change, review.startNetWorth) else null,
            movers = review.movers.map { (account, change) -> ReviewMover(account.account.id, account.account.name, delta(change, null)) },
            incomeText = money(review.income),
            spendingText = money(review.spending),
            savedText = money(review.saved),
            savingsRateText = review.savingsRate?.let(::percent),
            previousSpendingText = money(review.previousSpending),
            spendingChange = delta(review.spending - review.previousSpending, review.previousSpending),
            previousSavingsRateText = review.previousSavingsRate?.let(::percent),
            categories = review.topCategories.map { c ->
                ReviewCategory(
                    name = c.categoryId?.let { input.categories[it]?.name },
                    amountText = money(c.amount),
                    previousText = money(c.previous),
                    change = delta(c.amount - c.previous, c.previous),
                )
            },
            notUpdated = review.notUpdated.map { it.id to it.name },
        )
    }

    fun previous() = selected.update { it.minusMonths(1) }

    fun next() = selected.update { if (it < lastFinished) it.plusMonths(1) else it }

    companion object {
        fun factory(month: YearMonth?) = appViewModelFactory {
            MonthlyReviewViewModel(
                month, it.accountRepository, it.catalogRepository, it.currencyRepository, it.shareRepository, it.houseRepository,
                it.expenseRepository, it.loanRepository.loans, it.pensionRepository.pensions, it.calculatedAccounts, it.today.date,
            )
        }
    }
}
