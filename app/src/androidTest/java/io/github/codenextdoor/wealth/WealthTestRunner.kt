package io.github.codenextdoor.wealth

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import io.github.codenextdoor.wealth.data.rates.PriceSource
import io.github.codenextdoor.wealth.data.rates.Quote
import io.github.codenextdoor.wealth.data.rates.RateSource
import java.math.BigDecimal
import java.time.LocalDate

/** Runs on-device tests against [TestWealthApplication]. */
class WealthTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestWealthApplication::class.java.name, context)

    /** After the app starts (on the main thread): StrictMode findings become test failures. */
    override fun callApplicationOnCreate(app: Application) {
        super.callApplicationOnCreate(app)
        StrictModeViolations.watch()
    }
}

/** The real app, but with an in-memory database, separate settings files and no internet. */
class TestWealthApplication : WealthApplication() {
    override fun createContainer() = AppContainer(this, forTests = true, rateSource = TestRates, priceSource = TestPrices)
}

/** "1 CHF = 100 INR" and "1 CHF = 1.25 USD" on every day; the tests never go online. */
object TestRates : RateSource {
    override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate): Map<String, Quote> =
        if (base != "CHF") {
            emptyMap()
        } else {
            mapOf("INR" to Quote(BigDecimal("100"), date), "USD" to Quote(BigDecimal("1.25"), date)).filterKeys { it in currencies }
        }
}

/** GOOG closes at 150 every day; other shares have no price. */
object TestPrices : PriceSource {
    override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate): Map<LocalDate, BigDecimal> =
        if (symbol != "GOOG") emptyMap() else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { BigDecimal("150") }
}
