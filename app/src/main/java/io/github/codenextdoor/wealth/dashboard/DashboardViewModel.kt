package io.github.codenextdoor.wealth.dashboard

import io.github.codenextdoor.wealth.domain.AssetClass
import io.github.codenextdoor.wealth.domain.formatPercent
import androidx.lifecycle.ViewModel
import io.github.codenextdoor.wealth.domain.Property
import io.github.codenextdoor.wealth.domain.Loan
import io.github.codenextdoor.wealth.domain.Pension
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.NetWorthAccounts
import io.github.codenextdoor.wealth.domain.NetWorthCalculator
import io.github.codenextdoor.wealth.domain.Vesting
import io.github.codenextdoor.wealth.domain.RateBook
import io.github.codenextdoor.wealth.domain.PriceBook
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.data.repository.HouseRepository
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Trend
import io.github.codenextdoor.wealth.domain.ValuedAccount
import io.github.codenextdoor.wealth.domain.formatMoney
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import io.github.codenextdoor.wealth.ui.charts.ChartPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class ChartRange(val months: Long?) { SIX_MONTHS(6), ONE_YEAR(12), ALL(null) }

enum class BreakdownBy { TYPE, COUNTRY, CURRENCY, CLASS, ACCOUNT }

enum class ChangePeriod(val months: Long) { MONTH(1), YEAR(12) }

/** A signed change, pre-formatted. */
data class Delta(
    val amountText: String,
    val percentText: String?,
    val isIncrease: Boolean,
)

data class Slice(
    /** Null label means the "General (no country)" group; UI supplies the text. */
    val label: String?,
    val isOther: Boolean,
    val amountText: String,
    val percentText: String,
    val fraction: Float,
    /** Categorical color slot; ignored for [isOther]. */
    val colorSlot: Int,
    /** For [isOther]: the groups folded into it, largest first (the legend row opens to them). */
    val parts: List<Slice> = emptyList(),
    /** By class: which one (the UI names it; [label] is null then). */
    val assetClass: AssetClass? = null,
)

data class Mover(
    val accountId: Long,
    val name: String,
    val detail: String,
    val change: Delta,
)

/** An account whose first balance falls inside the chart; drawn at [pointDate], the chart point on top of its jump. */
data class AccountStartMarker(val pointDate: LocalDate, val name: String, val startedOn: LocalDate, val amountText: String)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val hasAccounts: Boolean = false,
    // Headline
    val netWorthText: String = "",
    val assetsText: String = "",
    val liabilitiesText: String = "",
    /** Change over the last month, or since history began if that's more recent. */
    val recentChange: Delta? = null,
    /** Set when history is shorter than a month: the change is since this date. */
    val recentChangeSince: LocalDate? = null,
    /** The part of [recentChange] from exchange rates; null when there's none (e.g. one currency only). */
    val recentChangeFromRates: Delta? = null,
    val excludedCount: Int = 0,
    /** Currencies without an exchange rate, and shares without a price, that keep accounts out. */
    val missingRateCurrencies: List<String> = emptyList(),
    /** Value of stock grants still to vest; not part of net worth. Null without grants (or a price). */
    val unvestedText: String? = null,
    /** Houses minus their loans, when they're left out of net worth; null when they count. */
    val housesOutsideText: String? = null,
    /** Net value of the accounts the user left out of net worth; null when there are none. */
    val accountsOutsideText: String? = null,
    // Net worth over time
    val range: ChartRange = ChartRange.ONE_YEAR,
    val history: List<ChartPoint> = emptyList(),
    /** Accounts that start during the chart, so their jump isn't read as growth. */
    val accountStarts: List<AccountStartMarker> = emptyList(),
    val forecast: List<ChartPoint> = emptyList(),
    val trendPerMonth: Delta? = null,
    val projectionText: String? = null,
    val projectionMonths: Long = 12,
    // Breakdown
    val breakdownBy: BreakdownBy = BreakdownBy.TYPE,
    val slices: List<Slice> = emptyList(),
    val assetsTotalText: String = "",
    // What changed
    val changePeriod: ChangePeriod = ChangePeriod.MONTH,
    val periodChange: Delta? = null,
    val movers: List<Mover> = emptyList(),
    val moreMovers: Int = 0,
    val baseCurrency: String = "",
    val baseDecimals: Int = 2,
)

private data class Snapshot(
    val calculator: NetWorthCalculator,
    val types: Map<Long, AccountType>,
    val typeOrder: Map<Long, Int>,
    /** Account names, for the breakdown by account (ordered by name for colours). */
    val accountNames: Map<Long, String>,
    val countries: Map<Long, Country>,
    val countryOrder: Map<Long, Int>,
    val currencyOrder: Map<String, Int>,
    val baseDecimals: Int,
    val hasAccounts: Boolean,
    val unvested: BigDecimal?,
    val rates: RateBook,
    val housesOutside: BigDecimal?,
    val accountsOutside: BigDecimal?,
    val today: LocalDate,
)

private data class Selections(val range: ChartRange, val breakdownBy: BreakdownBy, val period: ChangePeriod)

class DashboardViewModel(
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
    shareRepository: ShareRepository,
    houseRepository: HouseRepository,
    today: StateFlow<LocalDate>,
    /** Calculated loans: valued from their terms (see LoanBalance). */
    loans: Flow<List<Loan>> = flowOf(emptyList()),
    /** Pensions growing with contributions (see PensionValue). */
    pensions: Flow<List<Pension>> = flowOf(emptyList()),
) : ViewModel() {

    private val range = MutableStateFlow(ChartRange.ONE_YEAR)
    private val breakdownBy = MutableStateFlow(BreakdownBy.TYPE)
    private val period = MutableStateFlow(ChangePeriod.MONTH)

    private val catalog = combine(catalogRepository.accountTypes, catalogRepository.countries) { t, c -> t to c }

    private data class Money(
        val currencies: List<Currency>,
        val base: String,
        val rates: RateBook,
        val prices: PriceBook,
        val grants: List<Grant>,
    )

    private val money = combine(
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
        currencyRepository.rateBook,
        shareRepository.prices,
        shareRepository.grants,
        ::Money,
    )

    private data class Houses(val houses: List<Property>, val counted: Boolean, val today: LocalDate, val loans: List<Loan>, val pensions: List<Pension>)

    private val houses = combine(houseRepository.houses, houseRepository.inNetWorth, today, loans, pensions, ::Houses)

    private val snapshot = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        catalog,
        money,
        houses,
    ) { accounts, entries, (types, countries), (currencies, base, rates, prices, grants), (houseList, housesCounted, today, loans, pensions) ->
        val typesById = types.associateBy { it.id }
        val decimals = currencies.associate { it.code to it.decimals }
        val split = NetWorthAccounts.split(accounts, entries, typesById, decimals, houseList, housesCounted, loans, pensions)
        val valued = split.counted
        fun outside(group: List<ValuedAccount>) =
            if (group.isEmpty()) null else NetWorthCalculator(group, rates, base, prices).netWorthAt(today)
        val housesOutside = outside(split.housesOutside)
        val accountsOutside = outside(split.chosenOut)
        val grantValues = grants.map { grant ->
            prices.priceAt(grant.symbol, today)?.let { price ->
                rates.current.convert(Vesting.unvested(grant, today) * price, grant.currencyCode, base)
            }
        }
        Snapshot(
            calculator = NetWorthCalculator(valued, rates, base, prices),
            unvested = if (grantValues.isEmpty() || grantValues.any { it == null }) null else grantValues.fold(BigDecimal.ZERO) { s, v -> s + v!! },
            rates = rates,
            housesOutside = housesOutside,
            accountsOutside = accountsOutside,
            types = typesById,
            typeOrder = types.withIndex().associate { it.value.id to it.index },
            accountNames = accounts.associate { it.id to it.name },
            countries = countries.associateBy { it.id },
            countryOrder = countries.withIndex().associate { it.value.id to it.index },
            currencyOrder = currencies.withIndex().associate { (i, c: Currency) -> c.code to i },
            baseDecimals = decimals[base] ?: 2,
            hasAccounts = accounts.isNotEmpty(),
            today = today,
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        snapshot,
        combine(range, breakdownBy, period, ::Selections),
    ) { snap, sel -> build(snap, sel, snap.today) }
        .flowOn(Dispatchers.Default) // The history maths can take a moment with many entries.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectRange(value: ChartRange) { range.value = value }
    fun selectBreakdown(value: BreakdownBy) { breakdownBy.value = value }
    fun selectPeriod(value: ChangePeriod) { period.value = value }

    private fun build(snap: Snapshot, sel: Selections, today: LocalDate): DashboardUiState {
        val calc = snap.calculator
        val base = calc.baseCurrency
        val dec = snap.baseDecimals
        fun money(v: BigDecimal, decimals: Int = dec) = formatMoney(v, base, decimals)
        fun delta(change: BigDecimal, from: BigDecimal?, decimals: Int = dec) = Delta(
            amountText = (if (change.signum() < 0) "−" else "+") + money(change.abs(), decimals),
            percentText = from?.takeIf { it.signum() != 0 }?.let {
                val pct = change.multiply(BigDecimal(100)).divide(it.abs(), 1, RoundingMode.HALF_EVEN)
                (if (pct.signum() < 0) "−" else "+") + formatPercent(pct.abs())
            },
            isIncrease = change.signum() >= 0,
        )

        val totals = calc.totalsAt(today)
        val earliest = calc.earliestDate

        // Recent change: over the last month, or since history began if newer.
        val monthAgo = today.minusMonths(1)
        val changeFrom = earliest?.let { maxOf(it, monthAgo) }?.takeIf { it.isBefore(today) }
        val recentChange = changeFrom?.let {
            val before = calc.netWorthAt(it)
            delta(totals.netWorth - before, before)
        }
        val fromRates = changeFrom?.let { calc.currencyEffect(it, today).values.fold(BigDecimal.ZERO, BigDecimal::add) }
            ?.takeIf { it.setScale(dec, RoundingMode.HALF_EVEN).signum() != 0 }
            ?.let { delta(it, null) }

        // Net worth over time.
        val history = if (earliest != null && earliest.isBefore(today)) {
            val start = sel.range.months?.let { maxOf(earliest, today.minusMonths(it)) } ?: earliest
            calc.series(start, today).map { (date, value) -> ChartPoint(date, value.toFloat(), money(value)) }
        } else {
            emptyList()
        }
        // Accounts starting during the chart: marked on the first point on or after their first balance.
        val accountStarts = history.firstOrNull()?.let { first ->
            calc.accountsStarting(first.date, today).mapNotNull { start ->
                history.firstOrNull { !it.date.isBefore(start.date) }?.let { point ->
                    AccountStartMarker(point.date, start.account.account.name, start.date, money(start.added))
                }
            }
        }.orEmpty()
        // The trend always uses up to the last 12 months, whatever range is shown.
        val trend = earliest?.let { Trend.fit(calc.series(maxOf(it, today.minusMonths(12)), today, maxPoints = 52)) }
        val horizon = if (sel.range == ChartRange.SIX_MONTHS) 6L else 12L
        val forecast = if (trend != null && history.isNotEmpty()) {
            (1..horizon).map { m ->
                val date = today.plusMonths(m)
                val value = trend.project(totals.netWorth, today, date)
                ChartPoint(date, value.toFloat(), "~" + money(value, 0))
            }
        } else {
            emptyList()
        }

        // Breakdown of assets.
        val breakdown: Map<Any?, BigDecimal> = when (sel.breakdownBy) {
            BreakdownBy.TYPE -> calc.assetBreakdown(today) { it.account.accountTypeId }
            BreakdownBy.COUNTRY -> calc.assetBreakdown(today) { it.account.countryId }
            BreakdownBy.CURRENCY -> calc.assetBreakdown(today) { it.account.currencyCode }
            BreakdownBy.CLASS -> calc.assetBreakdown(today) { snap.types[it.account.accountTypeId]?.assetClass ?: AssetClass.OTHER }
            BreakdownBy.ACCOUNT -> calc.assetBreakdown(today) { it.account.id }
        }.filterValues { it.signum() > 0 }
        val slices = slicesFor(breakdown, sel.breakdownBy, snap, ::money)

        // What changed over the selected period.
        val periodStart = today.minusMonths(sel.period.months)
        val changes = if (earliest != null) calc.changesBetween(periodStart, today) else emptyList()
        val periodChange = earliest?.takeIf { it.isBefore(today) }?.let {
            val before = calc.netWorthAt(periodStart)
            delta(totals.netWorth - before, before)
        }
        val movers = changes.take(MAX_MOVERS).map { (account, change) ->
            val type = snap.types[account.account.accountTypeId]
            Mover(
                accountId = account.account.id,
                name = account.account.name,
                detail = listOfNotNull(type?.name, account.account.countryId?.let { snap.countries[it]?.name }).joinToString(" · "),
                change = delta(change, null),
            )
        }

        return DashboardUiState(
            isLoading = false,
            hasAccounts = snap.hasAccounts,
            netWorthText = money(totals.netWorth),
            assetsText = money(totals.assets),
            liabilitiesText = money(totals.liabilities),
            recentChange = recentChange,
            recentChangeFromRates = fromRates,
            recentChangeSince = changeFrom?.takeIf { it.isAfter(monthAgo) },
            excludedCount = calc.excluded.size,
            missingRateCurrencies = calc.excluded.map { valued ->
                val code = valued.account.currencyCode
                if (snap.rates.current.rate(code, base) == null) code else valued.account.shareSymbol ?: code
            }.distinct(),
            unvestedText = snap.unvested?.let { money(it) },
            housesOutsideText = snap.housesOutside?.let { money(it) },
            accountsOutsideText = snap.accountsOutside?.let { money(it) },
            range = sel.range,
            history = history,
            accountStarts = accountStarts,
            forecast = forecast,
            trendPerMonth = trend?.let { delta(it.perMonth, null, 0) },
            projectionText = forecast.lastOrNull()?.valueText,
            projectionMonths = horizon,
            breakdownBy = sel.breakdownBy,
            slices = slices,
            assetsTotalText = money(totals.assets),
            changePeriod = sel.period,
            periodChange = periodChange,
            movers = movers,
            moreMovers = (changes.size - MAX_MOVERS).coerceAtLeast(0),
            baseCurrency = base,
            baseDecimals = dec,
        )
    }

    /**
     * Largest groups first; beyond [MAX_SLICES] the smallest fold into "Other".
     * Colors follow each group's position in its settings list, not its size,
     * so a group keeps its color when values change.
     */
    private fun slicesFor(
        values: Map<Any?, BigDecimal>,
        by: BreakdownBy,
        snap: Snapshot,
        money: (BigDecimal, Int) -> String,
    ): List<Slice> {
        val total = values.values.fold(BigDecimal.ZERO, BigDecimal::add)
        if (total.signum() <= 0) return emptyList()
        val sorted = values.entries.sortedByDescending { it.value }
        val shown = if (sorted.size > MAX_SLICES) sorted.take(MAX_SLICES - 1) else sorted
        val rest = sorted.drop(shown.size)

        val accountOrder = snap.accountNames.entries.sortedBy { it.value.lowercase() }.withIndex().associate { it.value.key to it.index }
        fun order(key: Any?): Int = when (by) {
            BreakdownBy.TYPE -> snap.typeOrder[key] ?: Int.MAX_VALUE
            BreakdownBy.COUNTRY -> if (key == null) Int.MAX_VALUE else snap.countryOrder[key] ?: Int.MAX_VALUE - 1
            BreakdownBy.CURRENCY -> snap.currencyOrder[key] ?: Int.MAX_VALUE
            BreakdownBy.CLASS -> (key as AssetClass).ordinal
            BreakdownBy.ACCOUNT -> accountOrder[key] ?: Int.MAX_VALUE
        }
        val slotByKey = shown.map { it.key }.sortedBy(::order).withIndex().associate { it.value to it.index }

        fun label(key: Any?): String? = when (by) {
            BreakdownBy.TYPE -> snap.types[key]?.name
            BreakdownBy.COUNTRY -> (key as Long?)?.let { snap.countries[it]?.name }
            BreakdownBy.CURRENCY -> key as String
            BreakdownBy.CLASS -> null
            BreakdownBy.ACCOUNT -> snap.accountNames[key]
        }
        fun slice(label: String?, isOther: Boolean, value: BigDecimal, slot: Int): Slice {
            val fraction = value.divide(total, CurrencyConverter.MATH)
            return Slice(
                label = label,
                isOther = isOther,
                amountText = money(value, snap.baseDecimals),
                percentText = formatPercent(fraction.multiply(BigDecimal(100))),
                fraction = fraction.toFloat(),
                colorSlot = slot,
            )
        }
        fun classOf(key: Any?) = if (by == BreakdownBy.CLASS) key as AssetClass else null
        return shown.map { slice(label(it.key), false, it.value, slotByKey.getValue(it.key)).copy(assetClass = classOf(it.key)) } +
            if (rest.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    slice(null, true, rest.fold(BigDecimal.ZERO) { s, e -> s + e.value }, -1)
                        .copy(parts = rest.map { slice(label(it.key), false, it.value, -1).copy(assetClass = classOf(it.key)) }),
                )
            }
    }

    companion object {
        /** A donut stays readable up to six segments (five groups plus "Other"). */
        private const val MAX_SLICES = 6
        private const val MAX_MOVERS = 5

        val Factory = appViewModelFactory {
            DashboardViewModel(
                it.accountRepository, it.catalogRepository, it.currencyRepository, it.shareRepository, it.houseRepository, it.today.date,
                it.loanRepository.loans, it.pensionRepository.pensions,
            )
        }
    }
}
