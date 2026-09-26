package io.github.codenextdoor.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class NetWorthCalculatorTest {

    private val day0: LocalDate = LocalDate.of(2026, 1, 1)
    private val converter = RateBook(listOf(RatePoint("CHF", "INR", BigDecimal("100"), day0)))

    private fun account(
        id: Long,
        currency: String,
        kind: AssetKind,
        vararg balances: Pair<Int, Long>, // day offset -> minor units
        countryId: Long? = null,
    ) = ValuedAccount(
        account = Account(id, "A$id", id, currency, countryId, balances.last().second, Instant.EPOCH, null, null),
        kind = kind,
        decimals = 2,
        history = balances.map { (offset, minor) -> BalanceEntry(0, id, day0.plusDays(offset.toLong()), minor) },
    )

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun totalsConvertAndSubtractLiabilities() {
        val calc = NetWorthCalculator(
            listOf(
                account(1, "CHF", AssetKind.ASSET, 0 to 1_000_00),
                account(2, "INR", AssetKind.ASSET, 0 to 50_000_00), // = CHF 500
                account(3, "CHF", AssetKind.LIABILITY, 0 to 200_00),
            ),
            converter,
            "CHF",
        )
        val totals = calc.totalsAt(day0)
        assertAmount("1500", totals.assets)
        assertAmount("200", totals.liabilities)
        assertAmount("1300", totals.netWorth)
    }

    @Test
    fun usesLatestBalanceOnOrBeforeDate() {
        val calc = NetWorthCalculator(
            listOf(account(1, "CHF", AssetKind.ASSET, 0 to 100_00, 10 to 250_00)),
            converter,
            "CHF",
        )
        assertAmount("0", calc.netWorthAt(day0.minusDays(1)))
        assertAmount("100", calc.netWorthAt(day0.plusDays(9)))
        assertAmount("250", calc.netWorthAt(day0.plusDays(10)))
        assertAmount("250", calc.netWorthAt(day0.plusDays(400)))
    }

    @Test
    fun accountsWithoutRateAreExcluded() {
        val usd = account(2, "USD", AssetKind.ASSET, 0 to 100_00)
        val calc = NetWorthCalculator(listOf(account(1, "CHF", AssetKind.ASSET, 0 to 100_00), usd), converter, "CHF")
        assertEquals(listOf(usd), calc.excluded)
        assertAmount("100", calc.netWorthAt(day0))
    }

    @Test
    fun breakdownGroupsAssetsOnly() {
        val calc = NetWorthCalculator(
            listOf(
                account(1, "CHF", AssetKind.ASSET, 0 to 100_00, countryId = 1),
                account(2, "INR", AssetKind.ASSET, 0 to 30_000_00, countryId = 2),
                account(3, "CHF", AssetKind.ASSET, 0 to 50_00, countryId = 1),
                account(4, "CHF", AssetKind.LIABILITY, 0 to 999_00, countryId = 1),
            ),
            converter,
            "CHF",
        )
        val byCountry = calc.assetBreakdown(day0) { it.account.countryId }
        assertAmount("150", byCountry.getValue(1))
        assertAmount("300", byCountry.getValue(2))
        assertEquals(2, byCountry.size)
    }

    @Test
    fun changesShowContributionToNetWorth() {
        val calc = NetWorthCalculator(
            listOf(
                account(1, "CHF", AssetKind.ASSET, 0 to 100_00, 30 to 400_00),
                account(2, "CHF", AssetKind.LIABILITY, 0 to 100_00, 30 to 150_00), // more debt
                account(3, "CHF", AssetKind.ASSET, 0 to 70_00), // unchanged
            ),
            converter,
            "CHF",
        )
        val changes = calc.changesBetween(day0, day0.plusDays(30))
        assertEquals(listOf(1L, 2L), changes.map { it.first.account.id })
        assertAmount("300", changes[0].second)
        assertAmount("-50", changes[1].second)
    }

    @Test
    fun pastValuesUseRateOfThatDate() {
        val rates = RateBook(
            listOf(
                RatePoint("CHF", "INR", BigDecimal("80"), day0),
                RatePoint("CHF", "INR", BigDecimal("100"), day0.plusDays(30)),
            ),
        )
        val calc = NetWorthCalculator(listOf(account(1, "INR", AssetKind.ASSET, 0 to 8_000_00)), rates, "CHF")
        assertAmount("100", calc.netWorthAt(day0)) // ₹8,000 at 80
        assertAmount("80", calc.netWorthAt(day0.plusDays(30))) // same rupees, weaker rupee
    }

    @Test
    fun seriesEndsOnLastDateAndIsCapped() {
        val calc = NetWorthCalculator(listOf(account(1, "CHF", AssetKind.ASSET, 0 to 100_00)), converter, "CHF")
        val series = calc.series(day0, day0.plusDays(365), maxPoints = 12)
        assertEquals(day0.plusDays(365), series.last().first)
        assert(series.size in 12..14) { "size was ${series.size}" }
    }

    @Test
    fun earliestDateIgnoresExcludedAccounts() {
        val calc = NetWorthCalculator(
            listOf(account(1, "CHF", AssetKind.ASSET, 5 to 1), account(2, "USD", AssetKind.ASSET, 0 to 1)),
            converter,
            "CHF",
        )
        assertEquals(day0.plusDays(5), calc.earliestDate)
        assertNull(NetWorthCalculator(emptyList(), converter, "CHF").earliestDate)
    }
}
