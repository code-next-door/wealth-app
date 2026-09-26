package io.github.codenextdoor.wealth.domain

import java.math.BigDecimal
import java.math.MathContext

/**
 * Converts between currencies using the exchange rates the user entered.
 *
 * A rate entered one way (1 INR = 0.0095 CHF) also works the other way
 * (1 CHF = 105.26 INR). If no rate links two currencies directly, one
 * intermediate currency is tried (INR -> CHF -> USD), so rates entered
 * against an old base currency keep working after the base changes.
 */
class CurrencyConverter(rates: List<ExchangeRate>) {

    private val rateByPair: Map<Pair<String, String>, BigDecimal> = buildMap {
        // Rates as entered take priority over inverted ones.
        rates.forEach { put(it.from to it.to, it.rate) }
        rates.forEach { putIfAbsent(it.to to it.from, BigDecimal.ONE.divide(it.rate, MATH)) }
    }

    /** Units of [to] per 1 unit of [from], or null if the currencies aren't linked. */
    fun rate(from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        rateByPair[from to to]?.let { return it }
        for ((pair, firstLeg) in rateByPair) {
            if (pair.first != from) continue
            val secondLeg = rateByPair[pair.second to to] ?: continue
            return firstLeg.multiply(secondLeg, MATH)
        }
        return null
    }

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        rate(from, to)?.let { amount.multiply(it, MATH) }

    companion object {
        /** 34 significant digits: far more than any real rate or balance needs. */
        val MATH: MathContext = MathContext.DECIMAL128
    }
}
