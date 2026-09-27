package io.github.codenextdoor.wealth.imports

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

/**
 * PDF import end to end on the device: an invented statement in the UBS
 * layout (assets/fake-ubs-statement.pdf, written like bank PDFs are: text
 * runs per line and column, two pages) is read with Android's PDF engine
 * and parsed.
 *
 * Note: PDFs made with Android's own PdfDocument place each glyph
 * separately and extract one letter per line, so they're not used here.
 */
@RunWith(AndroidJUnit4::class)
class NativePdfImportTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val file = File(context.cacheDir, "native-pdf-test.pdf")

    @After
    fun cleanUp() {
        file.delete()
    }

    private fun readAsset(name: String): String {
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { NativePdfText.extract(it) }
    }

    @Test
    fun readsATwoPageUbsStatement() {
        assertTrue(NativePdfText.isSupported())
        val text = readAsset("fake-ubs-statement.pdf")
        val parser = UbsAccountStatementParser()
        assertTrue(text, parser.canParse(text))

        val parsed = parser.parse(text)
        assertEquals("CHF", parsed.currency)
        assertEquals(3, parsed.transactions.size)
        assertFalse(parsed.transactions.any { it.needsCheck }) // columns joined on the right lines
        assertEquals(0, BigDecimal("-2000.00").compareTo(parsed.transactions[0].amount))
        assertEquals("EXAMPLE PROPERTIES AG · STANDING ORDER · CH 8000 ZURICH", parsed.transactions[0].description)
        assertEquals("EXAMPLE TELECOM AG · PAYNET ORDER", parsed.transactions[1].description) // page footer dropped
        assertEquals(0, BigDecimal("7500.00").compareTo(parsed.transactions[2].amount))
        assertEquals(0, BigDecimal("15450.10").compareTo(parsed.closingBalance))
    }
}
