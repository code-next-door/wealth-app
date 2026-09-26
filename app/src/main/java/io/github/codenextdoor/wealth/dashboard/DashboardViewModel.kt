package io.github.codenextdoor.wealth.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.CurrencyConverter
import io.github.codenextdoor.wealth.domain.NetWorthCalculator
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

enum class BreakdownBy { TYPE, COUNTRY, CURRENCY }

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
)

data class Mover(
    val accountId: Long,
    val name: String,
    val detail: String,
    val change: Delta,
)

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
    val excludedCount: Int = 0,
    val missingRateCurrencies: List<String> = emptyList(),
    // Net worth over time
    val range: ChartRange = ChartRange.ONE_YEAR,
    val history: List<ChartPoint> = emptyList(),
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
    val countries: Map<Long, Country>,
    val countryOrder: Map<Long, Int>,
    val currencyOrder: Map<String, Int>,
    val baseDecimals: Int,
    val hasAccounts: Boolean,
)

private data class Selections(val range: ChartRange, val breakdownBy: BreakdownBy, val period: ChangePeriod)

class DashboardViewModel(
    accountRepository: AccountRepository,
    catalogRepository: CatalogRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val range = MutableStateFlow(ChartRange.ONE_YEAR)
    private val breakdownBy = MutableStateFlow(BreakdownBy.TYPE)
    private val period = MutableStateFlow(ChangePeriod.MONTH)

    private val catalog = combine(catalogRepository.accountTypes, catalogRepository.countries) { t, c -> t to c }

    private val money = combine(
        currencyRepository.currencies,
        currencyRepository.baseCurrency,
        currencyRepository.rateBook,
    ) { currencies, base, rates -> Triple(currencies, base, rates) }

    private val snapshot = combine(
        accountRepository.accounts,
        accountRepository.balanceEntries,
        catalog,
        money,
    ) { accounts, entries, (types, countries), (currencies, base, rates) ->
        val typesById = types.associateBy { it.id }
        val decimals = currencies.associate { it.code to it.decimals }
        val historyByAccount = entries.groupBy { it.accountId }
        val valued = accounts.mapNotNull { account ->
            val type = typesById[account.accountTypeId] ?: return@mapNotNull null
            ValuedAccount(
                account = account,
                kind = type.kind,
                decimals = decimals[account.currencyCode] ?: 2,
                history = historyByAccount[account.id].orEmpty().sortedBy { it.date },
            )
        }
        Snapshot(
            calculator = NetWorthCalculator(valued, rates, base),
            types = typesById,
            typeOrder = types.withIndex().associate { it.value.id to it.index },
            countries = countries.associateBy { it.id },
            countryOrder = countries.withIndex().associate { it.value.id to it.index },
            currencyOrder = currencies.withIndex().associate { (i, c: Currency) -> c.code to i },
            baseDecimals = decimals[base] ?: 2,
            hasAccounts = accounts.isNotEmpty(),
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        snapshot,
        combine(range, breakdownBy, period, ::Selections),
    ) { snap, sel -> build(snap, sel, LocalDate.now()) }
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
                (if (pct.signum() < 0) "−" else "+") + pct.abs().toPlainString() + "%"
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

        // Net worth over time.
        val history = if (earliest != null && earliest.isBefore(today)) {
            val start = sel.range.months?.let { maxOf(earliest, today.minusMonths(it)) } ?: earliest
            calc.series(start, today).map { (date, value) -> ChartPoint(date, value.toFloat(), money(value)) }
        } else {
            emptyList()
        }
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
            recentChangeSince = changeFrom?.takeIf { it.isAfter(monthAgo) },
            excludedCount = calc.excluded.size,
            missingRateCurrencies = calc.excluded.map { it.account.currencyCode }.distinct(),
            range = sel.range,
            history = history,
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

        fun order(key: Any?): Int = when (by) {
            BreakdownBy.TYPE -> snap.typeOrder[key] ?: Int.MAX_VALUE
            BreakdownBy.COUNTRY -> if (key == null) Int.MAX_VALUE else snap.countryOrder[key] ?: Int.MAX_VALUE - 1
            BreakdownBy.CURRENCY -> snap.currencyOrder[key] ?: Int.MAX_VALUE
        }
        val slotByKey = shown.map { it.key }.sortedBy(::order).withIndex().associate { it.value to it.index }

        fun label(key: Any?): String? = when (by) {
            BreakdownBy.TYPE -> snap.types[key]?.name
            BreakdownBy.COUNTRY -> (key as Long?)?.let { snap.countries[it]?.name }
            BreakdownBy.CURRENCY -> key as String
        }
        fun slice(label: String?, isOther: Boolean, value: BigDecimal, slot: Int): Slice {
            val fraction = value.divide(total, CurrencyConverter.MATH)
            return Slice(
                label = label,
                isOther = isOther,
                amountText = money(value, snap.baseDecimals),
                percentText = fraction.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "%",
                fraction = fraction.toFloat(),
                colorSlot = slot,
            )
        }
        return shown.map { slice(label(it.key), false, it.value, slotByKey.getValue(it.key)) } +
            if (rest.isEmpty()) emptyList() else listOf(slice(null, true, rest.fold(BigDecimal.ZERO) { s, e -> s + e.value }, -1))
    }

    companion object {
        /** A donut stays readable up to six segments (five groups plus "Other"). */
        private const val MAX_SLICES = 6
        private const val MAX_MOVERS = 5

        val Factory = appViewModelFactory {
            DashboardViewModel(it.accountRepository, it.catalogRepository, it.currencyRepository)
        }
    }
}
