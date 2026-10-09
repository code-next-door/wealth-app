package io.github.codenextdoor.wealth.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.export.CsvExporter
import io.github.codenextdoor.wealth.settings.ExportMessage
import io.github.codenextdoor.wealth.settings.ExportViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Settings › Backup › Export as CSV: writes the file that was picked and says how it went. */
@RunWith(AndroidJUnit4::class)
class ExportViewModelTest : DatabaseTest() {

    private val written = mutableMapOf<Uri, String>()
    private var briefExits = 0
    private val file = Uri.parse("content://test/export.csv")

    private fun viewModel(write: suspend (Uri, String) -> Unit = { uri, text -> written[uri] = text }) =
        ExportViewModel(CsvExporter(expenses, catalog, accounts, currencies), write) { briefExits++ }.cancelledAfterTest()

    @Test
    fun transactionsAreWrittenToThePickedFile() {
        runBlocking { expenses.save(expense("COOP ZURICH", 45_30)) }
        val vm = viewModel()
        vm.beforeFilePicker()
        assertEquals(1, briefExits) // the app lock lets the picker come back
        vm.exportTransactions(file)
        assertEquals(ExportMessage.TRANSACTIONS_EXPORTED, vm.state.await { it.message != null }.message)
        assertTrue(written.getValue(file).contains("COOP ZURICH,-45.30,CHF"))
    }

    @Test
    fun balancesAreWrittenToThePickedFile() {
        addAccount("Savings", balanceMinor = 1_000_00)
        val vm = viewModel()
        vm.exportBalances(file)
        assertEquals(ExportMessage.BALANCES_EXPORTED, vm.state.await { it.message != null }.message)
        assertTrue(written.getValue(file).contains(",Savings,Asset,"))
    }

    @Test
    fun aFileThatCantBeWrittenSaysSo() {
        val vm = viewModel { _, _ -> throw IOException("disk full") }
        vm.exportTransactions(file)
        assertEquals(ExportMessage.FAILED, vm.state.await { it.message != null }.message)
        vm.messageShown()
        assertEquals(null, vm.state.await { !it.busy }.message)
    }
}
