package io.github.codenextdoor.wealth.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StatementFileKind { PDF, CSV }

class StatementFile(val name: String, val kind: StatementFileKind, val text: String)

/**
 * Reads a statement file the user picked, entirely on the device: PDFs are
 * turned into text with PdfBox, CSVs are read as text. Nothing is uploaded.
 */
class StatementFileReader(private val context: Context) {

    private var pdfReady = false

    /** Null if the file can't be opened or read. */
    suspend fun read(uri: Uri): StatementFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = displayName(uri) ?: "statement"
            val isPdf = resolver.getType(uri) == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            if (isPdf) {
                if (!pdfReady) {
                    PDFBoxResourceLoader.init(context)
                    pdfReady = true
                }
                val text = resolver.openInputStream(uri)!!.use { input ->
                    PDDocument.load(input).use { document ->
                        // Reading order by position, matching what the parsers are tested against.
                        PDFTextStripper().apply { sortByPosition = true }.getText(document)
                    }
                }
                StatementFile(name, StatementFileKind.PDF, text)
            } else {
                val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                StatementFile(name, StatementFileKind.CSV, decode(bytes))
            }
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Bank exports are UTF-8 or, from older systems, Windows-1252. */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('�')) bytes.toString(charset("windows-1252")) else utf8
    }
}
