package io.github.codenextdoor.wealth

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import io.github.codenextdoor.wealth.data.rates.Quote
import io.github.codenextdoor.wealth.data.rates.RateSource
import java.math.BigDecimal
import java.time.LocalDate

/** Runs on-device tests against [TestWealthApplication]. */
class WealthTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestWealthApplication::class.java.name, context)
}

/** The real app, but with an in-memory database, separate settings files and no internet. */
class TestWealthApplication : WealthApplication() {
    override fun createContainer() = AppContainer(this, forTests = true, rateSource = TestRates)
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
