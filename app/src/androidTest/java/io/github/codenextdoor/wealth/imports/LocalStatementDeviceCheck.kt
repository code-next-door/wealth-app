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
 */
@RunWith(AndroidJUnit4::class)
class LocalStatementDeviceCheck {

    @Test
    fun readLocalStatement() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "local-check.pdf")
        assumeTrue("No local-check.pdf on the device", file.exists())
        val text = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { NativePdfText.extract(it) }
        val parser = ImportViewModel.PDF_PARSERS.firstOrNull { it.canParse(text) }
        if (parser == null) {
            Log.i(TAG, "No reader recognizes this layout (${text.lines().size} lines)")
            return
        }
        val parsed = parser.parse(text)
        Log.i(
            TAG,
            "${parsed.format}: ${parsed.transactions.size} rows, ${parsed.transactions.count { it.amount.signum() < 0 }} money out, " +
                "${parsed.transactions.count { it.needsCheck }} not matching the balance, closing balance found: ${parsed.closingBalance != null}",
        )
    }

    private companion object {
        const val TAG = "LocalStatementCheck"
    }
}
