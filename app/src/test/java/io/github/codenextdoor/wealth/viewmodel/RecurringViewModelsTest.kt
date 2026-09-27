package io.github.codenextdoor.wealth.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.repository.RecurringRepository
import io.github.codenextdoor.wealth.recurring.RecurringEditViewModel
import io.github.codenextdoor.wealth.recurring.RecurringListViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringViewModelsTest : DatabaseTest() {

    private val recurring by lazy { RecurringRepository(db) }

    private fun editor(id: Long? = null) = RecurringEditViewModel(
        SavedStateHandle(if (id == null) emptyMap() else mapOf(RecurringEditViewModel.ARG_RECURRING_ID to id)),
        recurring,
        expenses,
        catalog,
        currencies,
        accounts,
    ).also { vm -> vm.data.await { vm.uiState(it).isReady } }

    @Test
    fun newRecurringExpenseIsValidatedPreviewedAndAddsWhatIsDue() = runBlocking {
        val vm = editor()
        assertEquals("CHF", vm.uiState().currencyCode) // the base currency
        vm.save()
        assertTrue(vm.uiState().descriptionError && vm.uiState().amountError)

        vm.fields.description.setTextAndPlaceCursorAtEnd("Swisscom mobile")
        // The category follows the rules, like a normal expense.
        assertEquals(categoryId("utilities"), vm.data.await { vm.uiState(it).categoryId != null }.let { vm.uiState(it).categoryId })
        vm.fields.amount.setTextAndPlaceCursorAtEnd("49.90")
        vm.onStartDateChange(today.minusMonths(2))
        assertEquals(3, vm.uiState().dueNow) // two past months and today
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val added = expenses.expensesBetween(today.minusMonths(3), today).first()
        assertEquals(3, added.size)
        assertTrue(added.all { it.amountMinor == 49_90L && it.categoryId == categoryId("utilities") })
    }

    @Test
    fun anEndBeforeTheStartIsRejected() {
        val vm = editor()
        vm.fields.description.setTextAndPlaceCursorAtEnd("Gym")
        vm.fields.amount.setTextAndPlaceCursorAtEnd("80")
        vm.onEndDateChange(today.minusDays(1))
        vm.save()
        assertTrue(vm.uiState().endDateError)
        assertFalse(vm.uiState().isFinished)
        vm.onEndDateChange(null)
        assertFalse(vm.uiState().endDateError)
    }

    @Test
    fun listShowsEachWithItsNextDayAndDeletingKeepsExpenses() = runBlocking {
        val vm = editor()
        vm.fields.description.setTextAndPlaceCursorAtEnd("Rent")
        vm.fields.amount.setTextAndPlaceCursorAtEnd("2000")
        vm.onIntervalChange(1)
        vm.onStartDateChange(today.minusMonths(1))
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }

        val list = RecurringListViewModel(recurring, catalog, currencies).uiState.await { it.rows.isNotEmpty() }
        val row = list.rows.single()
        assertEquals("Rent", row.description)
        assertTrue(row.amountText.contains("2,000"))
        assertEquals(1, row.intervalMonths)
        assertTrue(row.nextDate!!.isAfter(today))

        editor(row.id).delete()
        eventually { runBlocking { recurring.recurring.first().isEmpty() } }
        assertEquals(2, expenses.expensesBetween(today.minusMonths(2), today).first().size)
    }
}
