package io.github.codenextdoor.wealth.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.export.CsvExporter
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportMessage { TRANSACTIONS_EXPORTED, BALANCES_EXPORTED, FAILED }

data class ExportUiState(val busy: Boolean = false, val message: ExportMessage? = null)

/** Settings › Backup › Export as CSV (transactions or balance history), to a file the user picks. */
class ExportViewModel(
    private val exporter: CsvExporter,
    /** Writes the text to the picked file (replacing what was there). */
    private val write: suspend (Uri, String) -> Unit,
    /** Lets the app lock ignore the trip to the system file picker. */
    private val allowBriefExit: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun beforeFilePicker() = allowBriefExit()

    fun exportTransactions(uri: Uri) = run(ExportMessage.TRANSACTIONS_EXPORTED) { write(uri, exporter.transactionsCsv()) }

    fun exportBalances(uri: Uri) = run(ExportMessage.BALANCES_EXPORTED) { write(uri, exporter.balancesCsv()) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun run(done: ExportMessage, work: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                work()
                done
            } catch (e: Exception) {
                ExportMessage.FAILED
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory = appViewModelFactory { container ->
            ExportViewModel(container.csvExporter, container.fileWriter::write, container.appLock::allowBriefExit)
        }
    }
}
