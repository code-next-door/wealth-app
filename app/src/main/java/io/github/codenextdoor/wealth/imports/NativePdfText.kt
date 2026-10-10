package io.github.codenextdoor.wealth.imports

import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import kotlin.math.abs

/**
 * Reads the text of a PDF with Android's own PDF engine: built in from
 * Android 15, and on Android 12-14 through Google Play system updates
 * (SDK extension level 13). Text comes back line by line, with pieces on
 * the same line (e.g. table columns) joined by spaces.
 */
object NativePdfText {

    class NotSupported : Exception("This Android version can't read PDF text")

    /** The PDF is locked with a password: none was given, or [wrongPassword]. */
    class Locked(val wrongPassword: Boolean) : Exception("This PDF needs a password")

    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

    /**
     * Text of all pages, in order. Amounts in a [CreditColumn] layout are marked.
     * Throws [NotSupported] when the phone can't do it, [Locked] when the PDF needs
     * a [password] (none, or a wrong one). The password is only used for this read.
     */
    fun extract(
        file: ParcelFileDescriptor,
        creditColumns: List<CreditColumn> = ImportViewModel.STATEMENT_PARSERS.mapNotNull { it.creditColumn },
        password: String? = null,
    ): String = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> extractV(file, creditColumns, password)
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 -> extractPreV(file, creditColumns, password)
            else -> throw NotSupported()
        }
    } catch (e: SecurityException) {
        // Android's way of saying "password needed or wrong".
        throw Locked(wrongPassword = password != null)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractV(file: ParcelFileDescriptor, columns: List<CreditColumn>, password: String?): String = (
        if (password == null) PdfRenderer(file) else PdfRenderer(file, LoadParams.Builder().setPassword(password).build())
        ).use { renderer ->
        val marker = CreditMarker(columns)
        (0 until renderer.pageCount).joinToString("\n") { index ->
            renderer.openPage(index).use { page ->
                marker.mark(page.textContents.joinToString("\n") { it.text }) { query -> page.searchText(query).map { it.bounds } }
            }
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun extractPreV(file: ParcelFileDescriptor, columns: List<CreditColumn>, password: String?): String = (
        if (password == null) PdfRendererPreV(file) else PdfRendererPreV(file, LoadParams.Builder().setPassword(password).build())
        ).use { renderer ->
        val marker = CreditMarker(columns)
        (0 until renderer.pageCount).joinToString("\n") { index ->
            renderer.openPage(index).use { page ->
                marker.mark(page.textContents.joinToString("\n") { it.text }) { query -> page.searchText(query).map { it.bounds } }
            }
        }
    }

    /**
     * Appends [CreditColumn.MARK] to amount lines printed in the "Credit" column,
     * page by page. The column positions come from the table header, which may
     * only be on the first page; later pages of the same document reuse them.
     * The n-th line with an amount goes with the n-th match of it (top to
     * bottom) in the Debit/Credit columns.
     */
    internal class CreditMarker(private val columns: List<CreditColumn>) {
        private var column: CreditColumn? = null
        private var debitRight = 0f
        private var creditRight = 0f

        /** [search] finds text on the page: the boxes of each match. */
        fun mark(text: String, search: (String) -> List<List<RectF>>): String {
            columns.firstOrNull { text.contains(it.header) }?.let { found ->
                val headerTop = search(found.header).firstOrNull()?.firstOrNull()?.top ?: return text
                fun headerRight(word: String) = search(word).flatten().firstOrNull { abs(it.top - headerTop) < 3 }?.right
                debitRight = headerRight("Debit") ?: return text
                creditRight = headerRight("Credit") ?: return text
                column = found
            }
            val column = column ?: return text
            val seen = mutableMapOf<String, Int>()
            return text.lines().joinToString("\n") { line ->
                val query = column.amountLine.matchEntire(line)?.groupValues?.get(1) ?: return@joinToString line
                val occurrence = seen.merge(query, 1, Int::plus)!! - 1
                val box = search(query).mapNotNull { it.lastOrNull() }
                    .filter { it.right > debitRight - COLUMN_SLACK } // not the "Amount" column
                    .sortedBy { it.top }
                    .getOrNull(occurrence)
                if (box != null && abs(box.right - creditRight) < abs(box.right - debitRight)) line + CreditColumn.MARK else line
            }
        }
    }

    /** How far left of the Debit header an amount may end and still be in that column. */
    private const val COLUMN_SLACK = 30f
}
