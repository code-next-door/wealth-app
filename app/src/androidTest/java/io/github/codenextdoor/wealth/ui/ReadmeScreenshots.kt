package io.github.codenextdoor.wealth.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.data.repository.HouseDetails
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Takes the README's screenshots, on the test app's in-memory database with
 * made-up sample data (never real data). Skipped unless run by
 * `scripts/readme-screenshots.sh`, which copies them into docs/screenshots.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots : UiTest() {

    private val folder get() = File(context.filesDir, "readme").apply { mkdirs() }

    @Test
    fun capture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readme") == "true")
        runBlocking { sampleData() }

        openTab("Overview")
        waitForText("Net worth over time")
        shoot("overview")

        openTab("Accounts")
        waitForText("Assets")
        shoot("accounts")

        openTab("House")
        waitForText("Apartment")
        shoot("house")

        openTab("Spending")
        waitForText("By category")
        shoot("spending")

        openTab("Overview")
        rule.runOnUiThread { container.appearancePreferences.setFiguresHidden(true) }
        waitForText("••••", substring = true)
        shoot("hidden")
        rule.runOnUiThread { container.appearancePreferences.setFiguresHidden(false) }

        rule.runOnUiThread { container.tour.start() }
        rule.runOnUiThread { container.tour.next() } // the Accounts stop: bubble and arrow
        waitForText("2 of 6")
        shoot("tour")
        rule.runOnUiThread { container.tour.stop() }
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        Thread.sleep(800) // let charts and fades settle
        val image = rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A year of invented history: accounts in three currencies, a house with a loan, spending. */
    private suspend fun sampleData() {
        val types = container.catalogRepository.accountTypes.first().associateBy { it.seedKey }
        val countries = container.catalogRepository.countries.first().associateBy { it.name }
        val categories = container.catalogRepository.expenseCategories.first().associateBy { it.name }
        val today = LocalDate.now()
        // Older than the chart's one-year view, so its line starts smoothly.
        val months = 14
        val start = today.minusMonths(months.toLong()).withDayOfMonth(1)
        val rates = container.currencyRepository
        rates.setRate("CHF", "INR", BigDecimal("102"), start.minusDays(1))
        rates.setRate("CHF", "USD", BigDecimal("1.12"), start.minusDays(1))

        suspend fun account(name: String, type: String, currency: String, country: String?, from: Long, to: Long): Long {
            var id = 0L
            for (m in 0..months) {
                val day = if (m == months) today else YearMonth.from(start.plusMonths(m.toLong())).atEndOfMonth()
                // A steady rise with a little up and down, like real balances.
                val wobble = if (m % 3 == 1) -(to - from) / 40 else 0
                val amount = from + (to - from) * m / months + wobble
                val account = Account(id, name, types.getValue(type).id, currency, country?.let { countries.getValue(it).id }, amount, Instant.EPOCH, null, null)
                id = container.accountRepository.save(account, day, recordBalance = true)
            }
            return id
        }
        account("Salary account", "ch_bank", "CHF", "Switzerland", 18_400_00, 27_900_00)
        account("Pillar 3a", "ch_pillar3a", "CHF", "Switzerland", 31_000_00, 38_200_00)
        account("Brokerage", "ch_brokerage", "USD", "Switzerland", 12_000_00, 16_400_00)
        account("NRE savings", "in_nre", "INR", "India", 9_50_000_00, 12_80_000_00)
        account("Mutual funds", "in_mutual_funds", "INR", "India", 18_00_000_00, 23_40_000_00)
        account("Credit card", "credit_card", "CHF", null, 1_450_00, 1_120_00)
        val loan = account("Home loan", "loan", "INR", "India", 42_00_000_00, 38_50_000_00)
        container.houseRepository.save(
            HouseDetails(0, "Apartment", "INR", countries.getValue("India").id, 85_00_000_00, today.minusYears(4), BigDecimal("6"), loan),
        )

        val monthly = listOf(
            Triple("Rent", "Housing & rent", 2_150_00L),
            Triple("Supermarket", "Groceries", 540_00L),
            Triple("Restaurant", "Eating out", 240_00L),
            Triple("Train pass", "Transport", 118_00L),
            Triple("Streaming", "Subscriptions", 45_00L),
            Triple("Online shop", "Shopping", 190_00L),
        )
        for (m in 0..months) {
            val month = YearMonth.from(start.plusMonths(m.toLong()))
            monthly.forEachIndexed { i, (description, category, amount) ->
                val day = month.atDay(minOf(3 + i * 4, month.lengthOfMonth())).let { if (it.isAfter(today)) today else it }
                val varied = amount + (amount / 10) * ((m + i) % 5 - 2)
                container.expenseRepository.save(Expense(0, day, varied, "CHF", description, categories.getValue(category).id, false, null, null))
            }
            if (m % 4 == 2) {
                container.expenseRepository.save(Expense(0, month.atDay(12), 980_00, "CHF", "Flights", categories.getValue("Travel").id, false, null, null))
            }
            // Made-up salary (money in, negative), so the Spending tab shows income and what was saved.
            val payday = month.atDay(minOf(25, month.lengthOfMonth())).let { if (it.isAfter(today)) today else it }
            container.expenseRepository.save(Expense(0, payday, -8_400_00, "CHF", "Salary", categories.getValue("Salary").id, false, null, null))
        }
    }
}
