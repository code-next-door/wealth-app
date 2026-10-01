package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.testutil.withPlainSpaces
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
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import io.github.codenextdoor.wealth.domain.formatMoney
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

    private val loanRepository by lazy { io.github.codenextdoor.wealth.data.repository.LoanRepository(db) }

    private fun editor(id: Long? = null, kind: AssetKind? = null) = AccountEditViewModel(
        id,
        accounts,
        catalog,
        currencies,
        rateUpdater,
        shares,
        priceUpdater,
        kind = kind,
        loanRepository = loanRepository,
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
    fun anAccountCanBeLeftOutOfNetWorthAndBackIn() {
        val id = addAccount("Joint account", balanceMinor = 500_00)
        val vm = editor(id).ready()
        assertTrue(vm.uiState().form.inNetWorth) // existing accounts are counted
        vm.onInNetWorthChange(false)
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val saved = runBlocking { accounts.get(id)!! }
        assertTrue(saved.excludedFromNetWorth)
        assertEquals(500_00L, saved.balanceMinor)
        assertEquals(1, runBlocking { accounts.observeHistory(id).first().size }) // no new balance entry
        val reopened = editor(id).ready()
        assertFalse(reopened.uiState().form.inNetWorth)
        reopened.onInNetWorthChange(true)
        reopened.save()
        reopened.data.await { reopened.uiState(it).isFinished }
        assertFalse(runBlocking { accounts.get(id)!! }.excludedFromNetWorth)
    }

    @Test
    fun accountsLeftOutAreMarkedAndNotInTheTotals() {
        addAccount("Salary", balanceMinor = 1000_00)
        val joint = addAccount("Joint", balanceMinor = 400_00)
        runBlocking { accounts.save(accounts.get(joint)!!.copy(excludedFromNetWorth = true), today, recordBalance = false) }
        val state = AccountsViewModel(accounts, catalog, currencies, ShareRepository(db), io.github.codenextdoor.wealth.data.repository.HouseRepository(db, accounts), todayFlow)
            .cancelledAfterTest().uiState.await { it.assets.any { a -> a.leftOut } }
        assertFalse(state.assets.single { it.name == "Salary" }.leftOut)
        assertEquals("CHF 1’000.00", state.assetsTotalText.withPlainSpaces()) // the joint account isn't added
    }

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

    @Test
    fun historyShowsDebtsWithAMinusButKeepsThemStoredAsOwed() {
        val card = addAccount("Cashback card", typeSeedKey = "credit_card", balanceMinor = 1_200_00, countrySeedKey = null)
        runBlocking { accounts.addHistoryEntry(card, today.minusMonths(1), -50_00) } // overpaid: the card owes you
        addAccount("Salary", balanceMinor = 500_00)
        val vm = HistoryViewModel(accounts, catalog, currencies, rateUpdater, shares, priceUpdater).cancelledAfterTest()
        val rows = vm.uiState.await { it.months.flatMap { m -> m.second }.size == 3 }.months.flatMap { it.second }

        val owed = rows.single { it.accountId == card && it.balanceMinor == 1_200_00L } // stored as the amount owed
        assertTrue(owed.isLiability)
        // Written as the currency's own style writes a negative amount (Swiss: "CHF-1’200.00").
        assertEquals(formatMoney(BigDecimal("-1200.00"), "CHF", 2), owed.amountText)
        assertTrue(owed.amountText.contains('-') || owed.amountText.contains('−'))
        assertEquals("CHF 50.00", rows.single { it.balanceMinor == -50_00L }.amountText.withPlainSpaces())
        assertEquals("CHF 500.00", rows.single { !it.isLiability }.amountText.withPlainSpaces())
    }

    @Test
    fun historyShowsSharesAndCash() {
        val stockPlan = runBlocking {
            val type = catalog.accountTypes.first().single { it.holdsShares }
            accounts.save(
                Account(0, "Google Stocks", type.id, "USD", null, 0, java.time.Instant.EPOCH, null, null, shareSymbol = "GOOG", units = BigDecimal.ZERO),
                balanceDate = today, recordBalance = true,
            )
            accounts.accounts.first().single { it.shareSymbol != null }
        }
        runBlocking { accounts.addHistoryEntry(stockPlan.id, today.minusDays(1), 767_62, units = BigDecimal("1455.9")) }
        val vm = HistoryViewModel(accounts, catalog, currencies, rateUpdater, shares, priceUpdater).cancelledAfterTest()
        val row = vm.uiState.await { s -> s.months.flatMap { it.second }.any { it.units != null && it.balanceMinor == 767_62L } }
            .months.flatMap { it.second }.single { it.balanceMinor == 767_62L }
        assertEquals("1,455.9 GOOG + $767.62", row.amountText.withPlainSpaces()) // wraps on screen as needed
    }

    @Test
    fun addingFromASectionOffersOnlyThatKindOfType() {
        val liability = editor(kind = AssetKind.LIABILITY).ready()
        val types = liability.uiState().types
        assertTrue(types.isNotEmpty() && types.all { it.kind == AssetKind.LIABILITY })
        assertTrue(types.any { it.seedKey == "credit_card" })

        val asset = editor(kind = AssetKind.ASSET).ready()
        assertTrue(asset.uiState().types.all { it.kind == AssetKind.ASSET })

        // Editing offers the account's own kind.
        val card = addAccount("Card", typeSeedKey = "credit_card", countrySeedKey = null)
        val edit = editor(card).ready()
        assertTrue(edit.uiState().types.all { it.kind == AssetKind.LIABILITY })
    }

    @Test
    fun theFormSavesACalculatedLoanAndCanSwitchItOff() = runBlocking {
        val firstEmi = today.minusMonths(3)
        val vm = editor(kind = AssetKind.LIABILITY).ready()
        vm.fields.name.setTextAndPlaceCursorAtEnd("Home loan")
        vm.onTypeChange(typeId("mortgage"))
        assertTrue(vm.uiState().isLoanType)
        vm.onCalculateLoanChange(true)
        vm.fields.loanPrincipal.setTextAndPlaceCursorAtEnd("2000000")
        vm.fields.loanEmi.setTextAndPlaceCursorAtEnd("17356")
        vm.fields.loanRate.setTextAndPlaceCursorAtEnd("8.5")
        vm.onLoanFirstEmiChange(firstEmi)
        assertTrue(vm.addRateChange(firstEmi.plusMonths(2), "9", ""))
        assertFalse(vm.addRateChange(firstEmi, "nine", "")) // not a rate
        val preview = vm.uiState().loanOutstandingToday!!
        assertTrue(preview in 1..2_000_000_00L - 1)
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val id = accounts.accounts.first().single { it.name == "Home loan" }.id
        val loan = loanRepository.forAccount(id)!!
        assertEquals(2_000_000_00L, loan.principalMinor)
        assertEquals(firstEmi, loan.firstEmiDate)
        assertEquals(listOf(BigDecimal("9")), loan.rateChanges.map { it.yearlyRate })
        // The principal is the first known balance, the day before the first EMI.
        assertTrue(accounts.observeHistory(id).first().any { it.date == firstEmi.minusDays(1) && it.balanceMinor == 2_000_000_00L })

        // Opened again: the terms are there; switched off, it's a plain liability again.
        val edit = editor(id).ready()
        edit.data.await { edit.uiState(it).form.calculateLoan }
        assertEquals("8.5", edit.uiState().form.loanRateText)
        edit.onCalculateLoanChange(false)
        edit.save()
        edit.data.await { edit.uiState(it).isFinished }
        assertNull(loanRepository.forAccount(id))
    }

    @Test
    fun aRateChangeCanBeEdited() {
        val vm = editor(kind = AssetKind.LIABILITY).ready()
        vm.onTypeChange(typeId("mortgage"))
        vm.onCalculateLoanChange(true)
        val from = today.minusMonths(2)
        assertTrue(vm.addRateChange(from, "9", ""))
        assertTrue(vm.addRateChange(from.plusMonths(1), "9.5", ""))

        // A new rate, a new EMI and a later day for the first one.
        assertTrue(vm.editRateChange(from, from.plusDays(3), "8.75", "18000"))
        val changes = vm.uiState().form.loanRateChanges
        assertEquals(listOf(from.plusDays(3), from.plusMonths(1)), changes.map { it.from })
        assertEquals(BigDecimal("8.75"), changes.first().yearlyRate)
        assertEquals(18_000_00L, changes.first().emiMinor)

        // Invalid input changes nothing.
        assertFalse(vm.editRateChange(from.plusDays(3), from.plusDays(3), "a lot", ""))
        assertEquals(changes, vm.uiState().form.loanRateChanges)
    }

    @Test
    fun aCalculatedLoanNeedsItsTerms() {
        val vm = editor(kind = AssetKind.LIABILITY).ready()
        vm.fields.name.setTextAndPlaceCursorAtEnd("Car loan")
        vm.onTypeChange(typeId("loan"))
        vm.onCalculateLoanChange(true)
        vm.save()
        val state = vm.uiState()
        assertTrue(state.loanPrincipalError && state.loanEmiError && state.loanRateError)
        assertFalse(state.balanceError) // the plain balance isn't asked for
        assertFalse(state.isFinished)
    }
}
