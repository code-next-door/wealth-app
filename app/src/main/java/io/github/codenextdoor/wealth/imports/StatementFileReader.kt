package io.github.codenextdoor.wealth.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StatementFileKind { PDF, CSV, XLSX }

class StatementFile(val name: String, val kind: StatementFileKind, val text: String)

/** Where statement files come from; an interface so tests can supply their own. */
interface StatementSource {
    /**
     * Null if the file can't be opened or read. Throws [NativePdfText.NotSupported]
     * for a PDF on a phone that can't read PDF text.
     */
    suspend fun read(uri: Uri): StatementFile?
}

/**
 * Reads a statement file the user picked, entirely on the device: PDFs are
 * turned into text by Android's own PDF engine, CSVs are read as text.
 * Nothing is uploaded.
 */
class StatementFileReader(private val context: Context) : StatementSource {

    override suspend fun read(uri: Uri): StatementFile? = withContext(Dispatchers.IO) {
        if (isPdf(uri) && !NativePdfText.isSupported()) throw NativePdfText.NotSupported()
        runCatching {
            val resolver = context.contentResolver
            val name = displayName(uri) ?: "statement"
            if (isPdf(uri)) {
                val text = resolver.openFileDescriptor(uri, "r")!!.use { NativePdfText.extract(it) }
                StatementFile(name, StatementFileKind.PDF, text)
            } else if (isXlsx(uri)) {
                val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                StatementFile(name, StatementFileKind.XLSX, XlsxText.extract(bytes)!!)
            } else {
                val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                StatementFile(name, StatementFileKind.CSV, decode(bytes))
            }
        }.getOrNull()
    }

    private fun isPdf(uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" ||
            displayName(uri)?.endsWith(".pdf", ignoreCase = true) == true

    private fun isXlsx(uri: Uri): Boolean =
        context.contentResolver.getType(uri) == XLSX_TYPE ||
            displayName(uri)?.endsWith(".xlsx", ignoreCase = true) == true

    /** The file's name as the provider reports it, else the last part of its path. */
    private fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

    /** Bank exports are UTF-8 or, from older systems, Windows-1252. */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('�')) bytes.toString(charset("windows-1252")) else utf8
    }
}

/** An Excel .xlsx spreadsheet's file type. */
internal const val XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
