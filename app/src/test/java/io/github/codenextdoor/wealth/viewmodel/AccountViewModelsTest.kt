package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.ui.Routes
import androidx.navigation.testing.invoke
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.accounts.AccountEditViewModel
import io.github.codenextdoor.wealth.accounts.AccountsViewModel
import io.github.codenextdoor.wealth.accounts.HistoryViewModel
import io.github.codenextdoor.wealth.accounts.RateEntry
import io.github.codenextdoor.wealth.accounts.RateStatus
import io.github.codenextdoor.wealth.data.rates.PriceSource
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.rates.Quote
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.codenextdoor.wealth.data.rates.RateSource
import io.github.codenextdoor.wealth.data.rates.RateUpdater
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import java.time.LocalDate
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AccountViewModelsTest : DatabaseTest() {

    /** "1 CHF = 100 INR" for any day, unless offline. */
    private val rateSource = object : RateSource {
        var online = true
        override suspend fun ratesOn(base: String, currencies: Set<String>, date: LocalDate) =
            if (online && "INR" in currencies) mapOf("INR" to Quote(BigDecimal("100"), date)) else emptyMap()
    }

    private val rateUpdater by lazy { RateUpdater(currencies, accounts, rateSource) { today } }

    private val shares by lazy { ShareRepository(db) }

    /** GOOG closes at 150 every day. */
    private val priceUpdater by lazy {
        PriceUpdater(
            shares,
            accounts,
            object : PriceSource {
                override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate) =
                    if (symbol == "GOOG") mapOf(to to BigDecimal("150")) else emptyMap()
            },
        ) { today }
    }

    private fun editor(id: Long? = null) = AccountEditViewModel(
        SavedStateHandle(route = Routes.AccountEdit(id)),
        accounts,
        catalog,
        currencies,
        rateUpdater,
        shares,
        priceUpdater,
    ).cancelledAfterTest()

    @Test
    fun sharesAccountSavesSymbolSharesCashAndPrice() {
        val vm = editor().ready()
        val type = vm.uiState().types.single { it.holdsShares }
        vm.onNameChange("Stock plan")
        vm.onTypeChange(type.id)
        vm.onCurrencyChange("USD")
        assertTrue(vm.uiState().holdsShares)
        vm.save()
        assertTrue(vm.uiState().symbolError && vm.uiState().unitsError)
        assertFalse(vm.uiState().balanceError) // cash is optional

        vm.fields.symbol.setTextAndPlaceCursorAtEnd("goog")
        vm.fields.units.setTextAndPlaceCursorAtEnd("12.5")
        vm.priceLookups.request("GOOG", today)
        eventually { vm.priceLookups.statusFor("GOOG", today) is RateStatus.Found }
        assertEquals("150.00", vm.data.await { vm.uiState(it).priceModel?.defaultText == "150.00" }.let { vm.uiState(it).priceText })
        vm.fields.price.setTextAndPlaceCursorAtEnd("151") // typed over the download
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val account = runBlocking { accounts.accounts.first().single() }
        assertEquals("GOOG", account.shareSymbol)
        assertEquals(0, BigDecimal("12.5").compareTo(account.units))
        assertEquals(0L, account.balanceMinor)
        val price = runBlocking { shares.prices.first().pointAt("GOOG", today)!! }
        assertEquals(0, BigDecimal("151").compareTo(price.price))
        assertFalse(price.fetched)
    }

    private fun point(date: LocalDate = today) = runBlocking { currencies.rateBook.first().pointAt("CHF", "INR", date) }

    /** Asks for the day's rate, as the screen does when the currency or date changes. */
    private fun AccountEditViewModel.download(date: LocalDate = today): RateStatus {
        rateLookups.request("INR", date)
        eventually { rateLookups.statusFor("INR", date) !is RateStatus.Loading }
        return rateLookups.statusFor("INR", date)
    }

    @Test
    fun downloadedRateFillsTheFieldAndIsSavedAsDownloaded() {
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor(id).ready()
        assertTrue(vm.download() is RateStatus.Found)
        vm.data.await { vm.uiState(it).rateText == "100" } // the saved download becomes the field's value
        vm.onBalanceChange("500")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertTrue(point()!!.fetched) // left as downloaded, so later downloads may update it
    }

    @Test
    fun typedRateWinsOverTheDownloadedOne() {
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor(id).ready()
        vm.download()
        vm.onRateChange("95")
        vm.onBalanceChange("500")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertFalse(point()!!.fetched)
        assertEquals(0, BigDecimal("95").compareTo(point()!!.rate))
    }

    @Test
    fun aTypedRateStaysButTheDownloadedOneCanBeChosen() {
        setRate("CHF", "INR", "90")
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor(id).ready()
        vm.download()
        val model = vm.data.await { vm.uiState(it).rateModel?.fetchedText != null }.let { vm.uiState(it).rateModel!! }
        assertEquals("90", vm.uiState().rateText) // the typed rate is kept
        assertEquals("100", model.fetchedText) // ...and the downloaded one offered
        vm.onRateChange(model.fetchedText!!) // "Use downloaded rate"
        vm.onBalanceChange("500")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertTrue(point()!!.fetched)
        assertEquals(0, BigDecimal("100").compareTo(point()!!.rate))
    }

    @Test
    fun offlineTheFieldKeepsTheLastKnownRate() {
        setRate("CHF", "INR", "90", today.minusMonths(1))
        rateSource.online = false
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor(id).ready()
        assertEquals(RateStatus.Unavailable, vm.download())
        assertEquals("90", vm.uiState().rateText)
    }

    /** Waits until the form is loaded and lists are available. */
    private fun AccountEditViewModel.ready() = also { vm -> vm.data.await { vm.uiState(it).isReady && vm.uiState(it).currencies.isNotEmpty() } }

    @Test
    fun accountsListSplitsAssetsAndLiabilitiesAndConverts() {
        setRate("CHF", "INR", "100")
        addAccount("Salary", balanceMinor = 1000_00)
        addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", balanceMinor = 50_000_00, countrySeedKey = "in")
        addAccount("Dollars", typeSeedKey = "ch_brokerage", currency = "USD")
        addAccount("Card", typeSeedKey = "credit_card", balanceMinor = 200_00, countrySeedKey = null)

        val state = AccountsViewModel(accounts, catalog, currencies, ShareRepository(db), io.github.codenextdoor.wealth.data.repository.HouseRepository(db, accounts), todayFlow).cancelledAfterTest().uiState.await { !it.isLoading && it.assets.size == 3 }
        assertEquals(listOf("Card"), state.liabilities.map { it.name })
        val nre = state.assets.single { it.name == "NRE" }
        assertTrue(nre.baseValueText!!.contains("500"))
        assertEquals("NRE account · India", nre.details)
        assertEquals("USD", state.assets.single { it.name == "Dollars" }.missingRateFor)
        assertNull(state.assets.single { it.name == "Salary" }.baseValueText)
        assertTrue(state.assetsTotalText.contains("1,500") || state.assetsTotalText.contains("1’500") || state.assetsTotalText.contains("1'500"))
    }

    @Test
    fun newAccountFormValidatesAndSaves() {
        val vm = editor().ready()
        assertEquals("CHF", vm.uiState().form.currencyCode) // defaults to base currency

        vm.save()
        val invalid = vm.uiState()
        assertTrue(invalid.nameError && invalid.typeError && invalid.balanceError)
        assertFalse(invalid.isFinished)

        vm.onNameChange("NRE savings")
        vm.onTypeChange(typeId("in_nre"))
        assertEquals(countryId("in"), vm.uiState().form.countryId) // country follows the type
        vm.onCurrencyChange("INR")
        vm.onBalanceChange("8,40,000")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val saved = runBlocking { accounts.accounts.first().single() }
        assertEquals("NRE savings", saved.name)
        assertEquals(840_000_00L, saved.balanceMinor)
        assertEquals(countryId("in"), saved.countryId)
    }

    @Test
    fun tooManyDecimalsIsAnError() {
        val vm = editor().ready()
        vm.onNameChange("X")
        vm.onTypeChange(typeId("cash"))
        vm.onBalanceChange("10.123")
        vm.save()
        assertTrue(vm.uiState().balanceError)
    }

    @Test
    fun countryChosenByUserIsNotOverwrittenByType() {
        val vm = editor().ready()
        vm.onCountryChange(countryId("ch"))
        vm.onTypeChange(typeId("in_nre"))
        assertEquals(countryId("ch"), vm.uiState().form.countryId)
    }

    @Test
    fun editingOnlyTheNameAddsNoBalanceHistory() {
        val id = addAccount("Salary", balanceMinor = 500_00, date = today.minusDays(3))
        val vm = editor(id).ready()
        assertEquals("500", vm.uiState().form.balanceText)
        vm.onNameChange("Main account")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertEquals(1, runBlocking { accounts.observeHistory(id).first().size })
    }

    @Test
    fun foreignCurrencyBalanceShowsAndSavesTheRateOfThatDay() {
        setRate("CHF", "INR", "105")
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", balanceMinor = 1_000_00, countrySeedKey = "in")
        val vm = editor(id).ready()
        val model = vm.uiState().rateModel!!
        assertEquals("CHF", model.from) // "1 CHF = 105 INR" reads better than 0.0095
        assertEquals("105", vm.uiState().rateText)

        val past = today.minusMonths(2)
        vm.onBalanceDateChange(past)
        vm.onBalanceChange("900")
        vm.onRateChange("95")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val book = runBlocking { currencies.rateBook.first() }
        assertEquals(0, BigDecimal("95").compareTo(book.converterAt(past).rate("CHF", "INR")))
        assertEquals(0, BigDecimal("105").compareTo(book.current.rate("CHF", "INR"))) // today unchanged
        assertEquals(1_000_00L, runBlocking { accounts.get(id)!!.balanceMinor }) // past entry isn't current
    }

    @Test
    fun invalidRateBlocksSaving() {
        setRate("CHF", "INR", "105")
        val id = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor(id).ready()
        vm.onRateChange("abc")
        vm.save()
        assertTrue(vm.uiState().rateError)
        assertFalse(vm.uiState().isFinished)
    }

    @Test
    fun historyEditsAreReflectedInTheForm() {
        val id = addAccount("Salary", balanceMinor = 500_00)
        val vm = editor(id).ready()
        val entry = vm.data.await { vm.uiState(it).history.isNotEmpty() }.let { vm.uiState(it).history.single() }
        vm.editHistoryEntry(entry.id, today, 650_00, null)
        eventually { vm.uiState().form.balanceText == "650" }
        vm.addHistoryEntry(today.minusYears(1), 100_00, RateEntry("CHF", "INR", BigDecimal("90")))
        vm.data.await { vm.uiState(it).history.size == 2 }
        assertNotNull(runBlocking { currencies.rateBook.first().converterAt(today.minusYears(1)).rate("CHF", "INR") })
    }

    @Test
    fun deleteAccount() {
        val id = addAccount("Salary")
        val vm = editor(id).ready()
        vm.delete()
        vm.data.await { vm.uiState(it).isFinished }
        assertNull(runBlocking { accounts.get(id) })
    }

    @Test
    fun historyScreenListsFiltersAndEditsEntries() {
        val salary = addAccount("Salary", balanceMinor = 500_00)
        runBlocking { accounts.addHistoryEntry(salary, today.minusMonths(1), 400_00) }
        addAccount("Cash", typeSeedKey = "cash", balanceMinor = 50_00, countrySeedKey = null)
        val vm = HistoryViewModel(accounts, catalog, currencies, rateUpdater, shares, priceUpdater).cancelledAfterTest()

        assertEquals(3, vm.uiState.await { it.months.flatMap { m -> m.second }.size == 3 }.months.sumOf { it.second.size })
        vm.selectAccount(salary)
        val filtered = vm.uiState.await { it.selectedAccountId == salary }.months.flatMap { it.second }
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.canDelete })

        val old = filtered.single { it.balanceMinor == 400_00L }
        vm.updateEntry(old.entryId, old.date, 420_00, null)
        vm.uiState.await { s -> s.months.flatMap { it.second }.any { it.balanceMinor == 420_00L } }
    }
}
