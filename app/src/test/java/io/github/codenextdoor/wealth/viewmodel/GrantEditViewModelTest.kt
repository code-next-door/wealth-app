package io.github.codenextdoor.wealth.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.grants.GrantEditViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import io.github.codenextdoor.wealth.data.rates.PriceSource
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@RunWith(AndroidJUnit4::class)
class GrantEditViewModelTest : DatabaseTest() {

    private val shares by lazy { ShareRepository(db) }

    private fun editor(id: Long? = null) = GrantEditViewModel(
        id,
        shares,
        currencies,
        PriceUpdater(
            shares,
            accounts,
            object : PriceSource {
                override suspend fun closes(symbol: String, from: LocalDate, to: LocalDate) = mapOf(to to BigDecimal("150"))
            },
        ) { today },
        CoroutineScope(Dispatchers.Unconfined),
    ).cancelledAfterTest().also { vm -> vm.data.await { vm.uiState(it).isReady } }

    @Test
    fun newGrantIsValidatedPreviewedAndSaved() = runBlocking {
        val vm = editor()
        assertEquals("USD", vm.uiState().currencyCode) // share prices are usually in dollars
        vm.save()
        val errors = vm.uiState()
        assertTrue(errors.nameError && errors.symbolError && errors.unitsError)
        assertFalse(errors.isFinished)

        vm.fields.name.setTextAndPlaceCursorAtEnd("New hire")
        vm.fields.symbol.setTextAndPlaceCursorAtEnd(" goog ")
        vm.fields.units.setTextAndPlaceCursorAtEnd("100")
        vm.fields.months.setTextAndPlaceCursorAtEnd("48")
        vm.onVestStartChange(today.minusMonths(6).minusDays(1))
        val preview = vm.uiState().preview!!
        assertEquals(48, preview.vestCount)
        assertEquals("2.083", preview.unitsPerVest)
        assertEquals("12.498", preview.vestedSoFar) // 6 monthly vests

        vm.fields.cliff.setTextAndPlaceCursorAtEnd("60")
        assertTrue(vm.uiState().cliffError) // longer than the vesting
        vm.fields.cliff.setTextAndPlaceCursorAtEnd("")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        val saved = shares.grants.first().single()
        eventually { runBlocking { shares.prices.first().priceAt("GOOG", today) } != null } // price fetched for its value
        assertEquals("GOOG", saved.symbol)
        assertEquals(0, BigDecimal("100").compareTo(saved.totalUnits))
        assertEquals(1, saved.intervalMonths)
        assertEquals(0, saved.cliffMonths)
    }

    @Test
    fun quarterlyVestingMustFitTheLength() {
        val vm = editor()
        vm.onIntervalChange(3)
        vm.fields.months.setTextAndPlaceCursorAtEnd("10")
        vm.save()
        assertTrue(vm.uiState().monthsError)
        vm.fields.months.setTextAndPlaceCursorAtEnd("12")
        assertFalse(vm.uiState().monthsError)
    }

    @Test
    fun existingGrantLoadsEditsAndDeletes() = runBlocking {
        val id = shares.saveGrant(Grant(0, "Refresh", "GOOG", "USD", today, BigDecimal("40"), today, 12, 3, 0, "note"))
        val vm = editor(id)
        assertEquals("Refresh", vm.fields.name.text.toString())
        assertEquals("12", vm.fields.months.text.toString())
        assertEquals(3, vm.uiState().intervalMonths)
        vm.fields.units.setTextAndPlaceCursorAtEnd("44")
        vm.save()
        vm.data.await { vm.uiState(it).isFinished }
        assertEquals(0, BigDecimal("44").compareTo(shares.getGrant(id)!!.totalUnits))

        editor(id).delete()
        eventually { runBlocking { shares.getGrant(id) } == null }
        assertNull(shares.getGrant(id))
    }
}
