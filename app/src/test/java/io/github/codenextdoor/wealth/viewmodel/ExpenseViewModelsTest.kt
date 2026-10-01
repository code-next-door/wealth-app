package io.github.codenextdoor.wealth.viewmodel

import java.time.LocalDate
import io.github.codenextdoor.wealth.testutil.withPlainSpaces
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.expenses.ExpenseEditViewModel
import io.github.codenextdoor.wealth.expenses.ExpensesViewModel
import io.github.codenextdoor.wealth.expenses.RulesViewModel
import io.github.codenextdoor.wealth.expenses.UNCATEGORIZED
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.codenextdoor.wealth.domain.ExpensePart
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
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class ExpenseViewModelsTest : DatabaseTest() {

    private fun editor(id: Long? = null) = ExpenseEditViewModel(
        id,
        expenses, catalog, accounts, currencies,
    ).cancelledAfterTest().also { vm -> vm.data.await { vm.uiState(it).isReady && vm.uiState(it).categories.isNotEmpty() } }

    private fun digits(text: String) = text.filter { it.isDigit() }

    @Test
    fun categoryFollowsRulesWhileTyping() {
        val vm = editor()
        vm.onDescriptionChange("TWINT *COOP-4521 ZUERICH")
        assertEquals(categoryId("groceries"), vm.uiState().form.categoryId)
        assertEquals("COOP", vm.uiState().matchedRule!!.keyword)
        vm.onDescriptionChange("UBER EATS ZURICH")
        assertEquals(categoryId("eating_out"), vm.uiState().form.categoryId)
    }

    @Test
    fun theCategoryDropdownFollowsTheOrderFromSettings() {
        val order = runBlocking { catalog.expenseCategories.first() }.map { it.id }
        runBlocking { catalog.reorderExpenseCategories(order.reversed()) }
        val vm = editor()
        val data = vm.data.await { vm.uiState(it).categories.size == order.size }
        assertEquals(order.reversed(), vm.uiState(data).categories.map { it.id })
    }

    @Test
    fun receivedIsSavedAsMoneyInAndEditedThatWay() {
        val vm = editor()
        vm.onDescriptionChange("Bonus")
        vm.onAmountChange("1000")
        vm.onDirectionChange(received = true)
        vm.onCategoryChange(categoryId("salary"))
        vm.save()
        vm.data.await { vm.uiState(it).ruleSuggestion != null } // "always Salary for BONUS?"
        vm.declineRule()
        vm.data.await { vm.uiState(it).isFinished }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        assertEquals(-1_000_00L, saved.amountMinor)

        val edit = editor(saved.id) // ready: the form is filled in
        assertTrue(edit.uiState().form.received)
        assertEquals("1000", edit.uiState().form.amountText) // no minus sign in the field
        edit.onDirectionChange(received = false) // e.g. a salary correction
        edit.save()
        edit.data.await { edit.uiState(it).ruleSuggestion != null || edit.uiState(it).isFinished }
        if (!edit.uiState().isFinished) edit.declineRule()
        edit.data.await { edit.uiState(it).isFinished }
        assertEquals(1_000_00L, runBlocking { expenses.get(saved.id)!!.amountMinor })
    }

    @Test
    fun pickingAnIncomeCategorySwitchesToReceivedUnlessChosen() {
        val vm = editor()
        vm.onCategoryChange(categoryId("salary"))
        assertTrue(vm.uiState().form.received)

        val chosen = editor()
        chosen.onDirectionChange(received = false)
        chosen.onCategoryChange(categoryId("salary"))
        assertFalse(chosen.uiState().form.received) // the user said Spent
    }

    @Test
    fun incomeRulesOnlyApplyToReceivedInTheForm() {
        val vm = editor()
        vm.onDescriptionChange("SALARY SEPTEMBER")
        assertNull(vm.uiState().form.categoryId) // spent: the salary rule doesn't apply
        vm.onDirectionChange(received = true)
        assertEquals(categoryId("salary"), vm.uiState().form.categoryId)
    }

    @Test
    fun savingValidatesAndStoresTheRuleCategoryUnlocked() {
        val vm = editor()
        vm.save()
        assertTrue(vm.uiState().descriptionError && vm.uiState().amountError)
        vm.onDescriptionChange("MIGROS BERN")
        vm.onAmountChange("12.50")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        assertEquals(12_50L, saved.amountMinor)
        assertEquals(categoryId("groceries"), saved.categoryId)
        assertFalse(saved.categoryLocked)
    }

    @Test
    fun pickingAnAccountPicksItsCurrency() {
        val nre = addAccount("NRE", typeSeedKey = "in_nre", currency = "INR", countrySeedKey = "in")
        val vm = editor()
        vm.onAccountChange(nre)
        assertEquals("INR", vm.uiState().form.currencyCode)
    }

    @Test
    fun correctingACategoryOffersARuleAndAcceptingAppliesIt() {
        expenses.let { runBlocking { it.save(expense("BAECKEREI HUG LUZERN", 5_00)) } } // an older one, uncategorized
        val vm = editor()
        vm.onDescriptionChange("TWINT *BAECKEREI HUG")
        vm.onAmountChange("4.20")
        vm.onCategoryChange(categoryId("eating_out"))
        vm.save()
        val suggestion = vm.data.await { vm.uiState(it).ruleSuggestion != null }.let { vm.uiState(it).ruleSuggestion!! }
        assertEquals("BAECKEREI", suggestion.keyword) // TWINT skipped

        vm.acceptRule(suggestion.keyword)
        vm.data.await { vm.uiState(it).isFinished }
        val all = runBlocking { expenses.expensesBetween(today, today).first() }
        assertTrue(all.all { it.categoryId == categoryId("eating_out") }) // the older one too
        assertTrue(all.single { it.amountMinor == 4_20L }.categoryLocked)
    }

    @Test
    fun noSuggestionWhenTheRuleAlreadyAgrees() {
        val vm = editor()
        vm.onDescriptionChange("MIGROS")
        vm.onAmountChange("1")
        vm.onCategoryChange(categoryId("groceries"))
        vm.save()
        assertNull(vm.data.await { vm.uiState(it).isFinished }.let { vm.uiState(it).ruleSuggestion })
    }

    @Test
    fun editingAndDeletingAnExistingExpense() {
        runBlocking { expenses.save(expense("Book", 20_00, categoryId = categoryId("education"), locked = true)) }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        val vm = editor(saved.id)
        assertEquals("Book", vm.uiState().form.description)
        assertEquals("20", vm.uiState().form.amountText)
        vm.delete()
        vm.data.await { vm.uiState(it).isFinished }
        assertNull(runBlocking { expenses.get(saved.id) })
    }

    @Test
    fun splittingAnExpenseIntoPartsKeepsTheRestInItsCategory() {
        val groceries = categoryId("groceries")
        val shopping = categoryId("shopping")
        runBlocking { expenses.save(expense("MIGROS BERN", 120_00, categoryId = groceries)) }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        val vm = editor(saved.id)
        assertTrue(vm.uiState().parts.isEmpty())

        vm.split() // one empty part to fill in
        val part = vm.uiState().parts.single()
        vm.fields.parts.single().amount.setTextAndPlaceCursorAtEnd("30")
        vm.fields.parts.single().note.setTextAndPlaceCursorAtEnd("detergent")
        vm.onPartCategoryChange(part.key, shopping)
        // The rest is worked out, never typed: the parts always add up.
        assertEquals("9000", digits(vm.uiState().restText!!))
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val split = runBlocking { expenses.get(saved.id)!! }
        assertEquals(120_00L, split.amountMinor) // the bank row is unchanged
        assertEquals(groceries, split.categoryId)
        assertEquals(listOf(Triple(30_00L, shopping, "detergent")), split.parts.map { Triple(it.amountMinor, it.categoryId, it.note) })

        // Opening it again shows the parts; removing the split puts it back as one.
        val again = editor(saved.id)
        assertEquals(1, again.uiState().parts.size)
        assertEquals("30", again.fields.parts.single().amount.text.toString())
        again.unsplit()
        again.save()
        again.data.await { again.uiState(it).isFinished }
        assertTrue(runBlocking { expenses.get(saved.id)!!.parts.isEmpty() })
    }

    @Test
    fun partsCanNotTakeMoreThanTheWhole() {
        val vm = editor()
        vm.onDescriptionChange("MIGROS")
        vm.onAmountChange("50")
        vm.split()
        vm.fields.parts.single().amount.setTextAndPlaceCursorAtEnd("50")
        vm.save()
        // Nothing would be left for the expense's own category.
        assertTrue(vm.uiState().restError)
        assertFalse(vm.uiState().isFinished)
        vm.addPart()
        assertEquals(2, vm.uiState().parts.size)
        vm.fields.parts[0].amount.setTextAndPlaceCursorAtEnd("20")
        vm.save()
        assertTrue(vm.uiState().parts[1].amountError) // the new part has no amount yet
        vm.removePart(vm.uiState().parts[1].key)
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        assertEquals(listOf(20_00L), saved.parts.map { it.amountMinor })
        assertEquals(30_00L, saved.restMinor)
    }

    @Test
    fun aSplitRefundStoresEveryPartAsMoneyIn() {
        val vm = editor()
        vm.onDescriptionChange("Return")
        vm.onAmountChange("40")
        vm.onDirectionChange(received = true)
        vm.onCategoryChange(categoryId("groceries"))
        vm.split()
        vm.fields.parts.single().amount.setTextAndPlaceCursorAtEnd("10")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished || vm.uiState(it).ruleSuggestion != null }
        val saved = runBlocking { expenses.expensesBetween(today, today).first().single() }
        assertEquals(-40_00L, saved.amountMinor)
        assertEquals(listOf(-10_00L), saved.parts.map { it.amountMinor })
    }

    @Test
    fun theSpendingTabListsEachPartUnderItsCategory() {
        val groceries = categoryId("groceries")
        val shopping = categoryId("shopping")
        runBlocking {
            expenses.save(expense("MIGROS", 120_00, categoryId = groceries).copy(parts = listOf(ExpensePart(amountMinor = 30_00, categoryId = shopping))))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        val rows = state.days.flatMap { it.second }
        assertEquals(listOf("9000", "3000"), rows.map { digits(it.amountText) })
        assertTrue(rows.all { it.description == "MIGROS" && digits(it.partOfText!!) == "12000" })
        assertEquals("12000", digits(state.totalText))
        vm.toggleFilter(shopping)
        assertEquals(listOf("3000"), vm.uiState.await { it.filter == shopping }.days.flatMap { it.second }.map { digits(it.amountText) })
        // The year view counts the parts too.
        assertEquals("120", vm.yearState.await { !it.isLoading }.months[today.monthValue - 1].totalText?.filter { it.isDigit() })
    }

    @Test
    fun spendingSummaryFilterAndMonths() {
        setRate("CHF", "INR", "100")
        val groceries = categoryId("groceries")
        runBlocking {
            expenses.save(expense("COOP", 40_00, categoryId = groceries))
            expenses.save(expense("SWIGGY", 1_000_00, currency = "INR", categoryId = categoryId("eating_out")))
            expenses.save(expense("Mystery", 5_00))
            expenses.save(expense("Refund", -5_00, categoryId = groceries))
            expenses.save(expense("Last month", 100_00, date = today.minusMonths(1).withDayOfMonth(1), categoryId = groceries))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        assertEquals("5000", digits(state.totalText)) // 40 + 10 + 5 - 5
        assertEquals(3, state.slices.size)
        assertTrue(state.slices.any { it.key == UNCATEGORIZED })
        assertNotNull(state.vsPreviousText)
        assertFalse(state.spentMore) // 50 this month vs 100 last month

        vm.toggleFilter(groceries)
        val filtered = vm.uiState.await { it.filter == groceries }.days.flatMap { it.second }
        assertEquals(setOf("COOP", "Refund"), filtered.map { it.description }.toSet())
        assertTrue(filtered.single { it.description == "Refund" }.isRefund)
        vm.toggleFilter(groceries)
        assertNull(vm.uiState.await { it.filter == null }.filter)

        vm.previousMonth()
        val previous = vm.uiState.await { it.month == YearMonth.from(today).minusMonths(1) }
        assertEquals(listOf("Last month"), previous.days.flatMap { it.second }.map { it.description })
    }

    @Test
    fun categoriesSwitchedOffAreLeftOutOfSpendingButListedApart() {
        val groceries = categoryId("groceries")
        val transfers = categoryId("investments_transfers") // seeded switched off
        runBlocking {
            expenses.save(expense("COOP", 40_00, categoryId = groceries))
            expenses.save(expense("To broker", 2_000_00, categoryId = transfers))
            expenses.save(expense("To broker again", 500_00, date = today.withDayOfMonth(1), categoryId = transfers))
            expenses.save(expense("Mystery", 10_00)) // uncategorized always counts
            expenses.save(expense("Last month", 50_00, date = today.minusMonths(1).withDayOfMonth(1), categoryId = groceries))
            expenses.save(expense("Broker last month", 9_000_00, date = today.minusMonths(1).withDayOfMonth(1), categoryId = transfers))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        assertEquals("5000", digits(state.totalText)) // 40 + 10
        assertTrue(state.slices.none { it.key == transfers })
        assertFalse(state.spentMore) // 50 vs 50: last month's transfer doesn't count either
        assertEquals(setOf("COOP", "Mystery"), state.days.flatMap { it.second }.map { it.description }.toSet())
        assertEquals(setOf("To broker", "To broker again"), state.notCounted.map { it.second.description }.toSet())
        assertEquals("250000", digits(state.notCountedTotalText!!))

        val thisMonth = YearMonth.from(today)
        val year = vm.yearState.await { it.months.getOrNull(thisMonth.monthValue - 1)?.totalText != null }
        assertEquals("CHF 50", year.months[thisMonth.monthValue - 1].totalText!!.withPlainSpaces())

        // Switched back on: it's spending again, and the separate list is gone.
        runBlocking { catalog.setCountsAsSpending(transfers, true) }
        val counted = vm.uiState.await { it.notCounted.isEmpty() }
        assertEquals("255000", digits(counted.totalText))
        assertNull(counted.notCountedTotalText)
    }

    @Test
    fun incomeIsShownApartWithWhatWasSaved() {
        val groceries = categoryId("groceries")
        runBlocking {
            expenses.save(expense("COOP", 1_000_00, categoryId = groceries))
            expenses.save(expense("COOP refund", -100_00, categoryId = groceries)) // back into Groceries
            expenses.save(expense("Pay", -5_000_00, categoryId = categoryId("salary")))
            expenses.save(expense("Twint from a friend", -100_00)) // money in, no category: income
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses && it.incomeTotalText != null }
        assertEquals("90000", digits(state.totalText)) // 1000 - 100 refund
        assertEquals("CHF 5’100.00", state.incomeTotalText!!.withPlainSpaces())
        assertEquals("CHF 4’200.00", state.savedText!!.withPlainSpaces())
        assertEquals("82.4", state.savedPercentText!!.filter { it.isDigit() || it == '.' })
        // Income is in its own card; the day list keeps spending and the refund.
        assertEquals(setOf("Pay", "Twint from a friend"), state.income.map { it.second.description }.toSet())
        assertTrue(state.income.all { it.second.moneyIn })
        assertEquals(setOf("COOP", "COOP refund"), state.days.flatMap { it.second }.map { it.description }.toSet())
        assertTrue(state.days.flatMap { it.second }.single { it.description == "COOP refund" }.isRefund)
        assertTrue(state.slices.none { it.key == categoryId("salary") }) // the donut is spending only
    }

    @Test
    fun otherHoldsTheSmallestCategoriesAndEachCanFilter() {
        // Eight categories: the chart shows five plus "Other" with the three smallest.
        val keys = listOf("housing", "groceries", "eating_out", "transport", "utilities", "healthcare", "shopping", "travel")
        runBlocking {
            keys.forEachIndexed { i, key -> expenses.save(expense("Spent on $key", (900 - i * 100).toLong() * 100, categoryId = categoryId(key))) }
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.slices.size == 6 }
        val other = state.slices.last()
        assertNull(other.key)
        assertEquals(listOf("healthcare", "shopping", "travel").map(::categoryId), other.parts.map { it.key })
        assertEquals("90000", digits(other.amountText)) // 400 + 300 + 200
        assertEquals("40000", digits(other.parts.first().amountText))

        vm.toggleFilter(categoryId("shopping")) // a category inside "Other"
        val filtered = vm.uiState.await { it.filter == categoryId("shopping") }.days.flatMap { it.second }
        assertEquals(listOf("Spent on shopping"), filtered.map { it.description })
    }

    @Test
    fun anEmiInTheLoansCategoryCountsOnlyItsInterest() = runBlocking {
        // A calculated mortgage whose EMIs are imported into "Home loan".
        val homeLoan = runBlocking { catalog.addExpenseCategory("Home loan"); catalog.expenseCategories.first().single { it.name == "Home loan" }.id }
        val firstEmi = today.minusMonths(6)
        val account = addAccount("Mortgage", typeSeedKey = "mortgage", balanceMinor = 400_000_00, date = firstEmi.minusDays(1))
        val loanRepository = io.github.codenextdoor.wealth.data.repository.LoanRepository(db)
        val loan = io.github.codenextdoor.wealth.domain.Loan(account, 400_000_00, firstEmi, 3_000_00, java.math.BigDecimal("2"), emiCategoryId = homeLoan)
        loanRepository.save(loan)
        expenses.save(expense("Mortgage EMI", 3_000_00, categoryId = homeLoan))
        expenses.save(expense("COOP", 100_00, categoryId = categoryId("groceries")))

        val interest = io.github.codenextdoor.wealth.domain.LoanBalance.interestIn(loan, listOf(firstEmi.minusDays(1) to 400_000_00L), YearMonth.from(today))
        assertTrue(interest in 1..2_999_99)
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow, loanRepository.loans).cancelledAfterTest()
        val state = vm.uiState.await { s -> s.days.flatMap { it.second }.any { it.interestText != null } }
        assertEquals((interest + 100_00).toString(), digits(state.totalText)) // the interest and the groceries
        val emi = state.days.flatMap { it.second }.single { it.description == "Mortgage EMI" }
        assertEquals(interest.toString(), digits(emi.interestText!!))
        val principal = state.notCounted.single { it.second.principalRepaid }.second
        assertEquals((3_000_00 - interest).toString(), digits(principal.amountText))
    }

    @Test
    fun aMonthWithoutIncomeLooksAsBefore() {
        runBlocking { expenses.save(expense("COOP", 40_00, categoryId = categoryId("groceries"))) }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        assertNull(state.incomeTotalText)
        assertNull(state.savedText)
        assertTrue(state.income.isEmpty())
    }

    @Test
    fun theYearViewCountsSpendingNotIncome() {
        todayFlow.value = LocalDate.of(2026, 8, 15)
        runBlocking {
            expenses.save(expense("Rent", 2_000_00, date = LocalDate.of(2026, 8, 1)))
            expenses.save(expense("Pay", -5_000_00, date = LocalDate.of(2026, 8, 25), categoryId = categoryId("salary")))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val year = vm.yearState.await { it.months.getOrNull(7)?.totalText != null }
        assertEquals("CHF 2K", year.months[7].totalText!!.withPlainSpaces())
    }

    @Test
    fun aMonthWithOnlyNotCountedExpensesStillShowsThem() {
        runBlocking { expenses.save(expense("To broker", 2_000_00, categoryId = categoryId("investments_transfers"))) }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val state = vm.uiState.await { it.hasExpenses }
        assertEquals("000", digits(state.totalText))
        assertTrue(state.slices.isEmpty())
        assertTrue(state.days.isEmpty())
        assertEquals(listOf("To broker"), state.notCounted.map { it.second.description })
    }

    @Test
    fun aNewMonthMovesTheCurrentMonthOnButNotAnOlderOne() {
        val thisMonth = YearMonth.from(today)
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        assertFalse(vm.uiState.await { !it.isLoading }.canGoForward)
        vm.nextMonth() // nothing after the current month
        assertEquals(thisMonth, vm.uiState.value.month)

        todayFlow.value = thisMonth.plusMonths(1).atDay(1) // midnight on the 1st, app still open
        assertFalse(vm.uiState.await { it.month == thisMonth.plusMonths(1) }.canGoForward)

        vm.previousMonth() // looking back...
        assertTrue(vm.uiState.await { it.month == thisMonth }.canGoForward)
        todayFlow.value = thisMonth.plusMonths(2).atDay(1)
        // ...stays where it was, and can now go two months forward.
        vm.uiState.await { it.canGoForward && it.month == thisMonth }
        vm.nextMonth()
        vm.nextMonth()
        vm.nextMonth()
        assertFalse(vm.uiState.await { it.month == thisMonth.plusMonths(2) }.canGoForward)
    }

    @Test
    fun theYearViewTotalsEachMonthAndJumpsBetweenMonthsAndYears() {
        todayFlow.value = LocalDate.of(2026, 8, 15)
        runBlocking {
            expenses.save(expense("A", 40_00, date = LocalDate.of(2026, 3, 10)))
            expenses.save(expense("B", 60_00, date = LocalDate.of(2026, 3, 20)))
            expenses.save(expense("C", 200_00, date = LocalDate.of(2026, 7, 5)))
            expenses.save(expense("Last year", 30_00, date = LocalDate.of(2025, 11, 2)))
        }
        val vm = ExpensesViewModel(expenses, catalog, accounts, currencies, todayFlow).cancelledAfterTest()
        val year = vm.yearState.await { it.year == 2026 && it.months.getOrNull(6)?.totalText != null }
        assertEquals(12, year.months.size)
        assertEquals("CHF 100", year.months[2].totalText!!.withPlainSpaces()) // March: 40 + 60
        assertEquals(1f, year.months[6].fraction) // July, the biggest month
        assertEquals(0.5f, year.months[2].fraction)
        assertNull(year.months[0].totalText) // nothing in January
        assertEquals(listOf(8, 9, 10, 11), year.months.withIndex().filter { it.value.isFuture }.map { it.index }) // Sep–Dec
        assertTrue(year.months[7].isSelected) // August, the current month
        assertEquals("CHF 300", year.totalText!!.withPlainSpaces())
        assertTrue(year.canGoBack) // there's spending in 2025
        assertFalse(year.canGoForward)

        vm.showMonth(YearMonth.of(2026, 3))
        assertEquals(YearMonth.of(2026, 3), vm.uiState.await { it.month == YearMonth.of(2026, 3) }.month)
        assertTrue(vm.yearState.await { it.months[2].isSelected }.months[2].isSelected)
        vm.showMonth(YearMonth.of(2026, 10)) // hasn't started: ignored
        assertEquals(YearMonth.of(2026, 3), vm.uiState.value.month)

        vm.previousYear()
        val last = vm.yearState.await { it.year == 2025 && it.months[10].totalText != null }
        assertEquals("CHF 30", last.months[10].totalText!!.withPlainSpaces())
        assertFalse(last.canGoBack) // the first expense is from 2025
        assertTrue(last.canGoForward)
        assertTrue(last.months.none { it.isFuture })
        assertEquals(YearMonth.of(2026, 3), vm.uiState.value.month) // browsing a year keeps the month
        vm.previousYear() // no further back
        assertEquals(2025, vm.yearState.value.year)

        vm.showMonth(YearMonth.of(2025, 12))
        vm.uiState.await { it.month == YearMonth.of(2025, 12) }
        vm.nextMonth() // into January: the year view follows
        vm.yearState.await { it.year == 2026 && it.months[0].isSelected }
    }

    @Test
    fun rulesScreenTestsTextAndManagesRules() {
        val vm = RulesViewModel(expenses, catalog).cancelledAfterTest()
        vm.data.await { it.let { d -> vm.uiState(d).rules.isNotEmpty() } }
        vm.onTestTextChange("UBER *TRIP")
        assertEquals("UBER", vm.uiState().testMatch!!.keyword)
        assertTrue(vm.uiState().testMatch!!.countsAsSpending)
        vm.onTestTextChange("C/O UBS CARD CENTER")
        val cardRule = vm.uiState().testMatch!!
        assertEquals(categoryId("card_payments"), cardRule.categoryId)
        assertFalse(cardRule.countsAsSpending) // paying the card isn't spending: the test line says so

        val transfers = categoryId("investments_transfers")
        vm.save(null, "wise payments", transfers)
        vm.data.await { d -> vm.uiState(d).rules.any { it.keyword == "WISE PAYMENTS" && it.categoryId == transfers } }

        runBlocking { expenses.save(expense("MIGROS", 1_00)) }
        vm.reapply()
        assertEquals(1, vm.data.await { vm.uiState(it).reappliedCount != null }.let { vm.uiState(it).reappliedCount })
    }
}
