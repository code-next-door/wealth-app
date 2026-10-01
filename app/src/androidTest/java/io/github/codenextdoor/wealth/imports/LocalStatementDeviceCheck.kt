package io.github.codenextdoor.wealth.imports

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks the PDF readers against a real statement, on the device. Skipped
 * unless the app's files folder contains local-check.pdf (see CLAUDE.md for
 * how to place and remove it). Logs counts only, never transactions.
 *
 * With the instrumentation argument `-e layout true` it also logs the text as
 * Android extracts it, with every digit replaced by 9, to see a new layout
 * without seeing amounts, dates or account numbers. Clear logcat afterwards.
 */
@RunWith(AndroidJUnit4::class)
class LocalStatementDeviceCheck {

    @Test
    fun readLocalStatement() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val file = listOf("local-check.pdf", "local-check.xlsx").map { File(dir, it) }.firstOrNull { it.exists() }
        assumeTrue("No local-check.pdf or local-check.xlsx on the device", file != null)
        val text = if (file!!.extension == "xlsx") {
            XlsxText.extract(file.readBytes()).also { Log.i(TAG, "Spreadsheet read: ${it != null}, lines: ${it?.lines()?.size}") }.orEmpty()
        } else {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { NativePdfText.extract(it) }
        }
        if (InstrumentationRegistry.getArguments().getString("layout") == "true") {
            text.replace(Regex("\\d"), "9").lines().forEach { Log.i(TAG, "| $it") }
        }
        val parser = ImportViewModel.STATEMENT_PARSERS.firstOrNull { it.canParse(text) }
        if (parser == null) {
            Log.i(TAG, "No reader recognizes this layout (${text.lines().size} lines)")
            return
        }
        val parsed = parser.parse(text)
        Log.i(
            TAG,
            "${parsed.format}: ${parsed.transactions.size} rows, ${parsed.transactions.count { it.amount.signum() < 0 }} money out, " +
                "${parsed.transactions.count { it.needsCheck }} not matching the balance, closing balance found: ${parsed.closingBalance != null}, " +
                "closing date found: ${parsed.closingDate != null}, value needs a look: ${parsed.valueNeedsCheck}, holdings found: ${parsed.holdings != null}, holdings add up: ${parsed.holdings?.addsUp}, " +
                "opening balance found: ${parsed.openingBalance != null && parsed.openingDate != null}, history points: ${StatementHistory.points(parsed).size}, " +
                "running balances: ${parsed.balances.size}, months covered: ${parsed.balances.map { java.time.YearMonth.from(it.first) }.distinct().size}, " +
                "rows before the period: ${parsed.openingDate?.let { start -> parsed.transactions.count { !it.date.isAfter(start) } }}, " +
                "rows after it: ${parsed.closingDate?.let { end -> parsed.transactions.count { it.date.isAfter(end) } }}",
        )
    }

    private companion object {
        const val TAG = "LocalStatementCheck"
    }
}
