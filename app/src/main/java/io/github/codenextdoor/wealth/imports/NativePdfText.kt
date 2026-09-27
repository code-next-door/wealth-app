package io.github.codenextdoor.wealth.imports

import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension

/**
 * Reads the text of a PDF with Android's own PDF engine: built in from
 * Android 15, and on Android 12-14 through Google Play system updates
 * (SDK extension level 13). Text comes back line by line, with pieces on
 * the same line (e.g. table columns) joined by spaces.
 */
object NativePdfText {

    class NotSupported : Exception("This Android version can't read PDF text")

    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

    /** Text of all pages, in order. Throws [NotSupported] when the phone can't do it. */
    fun extract(file: ParcelFileDescriptor): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> extractV(file)
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 -> extractPreV(file)
        else -> throw NotSupported()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractV(file: ParcelFileDescriptor): String = PdfRenderer(file).use { renderer ->
        (0 until renderer.pageCount).joinToString("\n") { index ->
            renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun extractPreV(file: ParcelFileDescriptor): String = PdfRendererPreV(file).use { renderer ->
        (0 until renderer.pageCount).joinToString("\n") { index ->
            renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
        }
    }
}
