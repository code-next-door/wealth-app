package io.github.codenextdoor.wealth.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Files opened or shared from another app (Gmail, Files) become the screen to start on. */
@RunWith(AndroidJUnit4::class)
class IncomingFilesTest {

    private val pdf = Uri.parse("content://mail/attachment/1")
    private val csv = Uri.parse("content://files/2")

    @Test
    fun openingOneFileStartsAnImport() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(pdf, "application/pdf")
        assertEquals(Routes.ImportFile(pdf.toString()), IncomingFiles.route(intent))
    }

    @Test
    fun sharingOneFileStartsAnImport() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, pdf)
        assertEquals(Routes.ImportFile(pdf.toString()), IncomingFiles.route(intent))
    }

    @Test
    fun sharingSeveralFilesBuildsHistory() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(pdf, csv))
        assertEquals(Routes.BackfillFiles(listOf(pdf.toString(), csv.toString())), IncomingFiles.route(intent))
    }

    @Test
    fun sharingOneFileInAListIsAnImport() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/pdf")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(pdf))
        assertEquals(Routes.ImportFile(pdf.toString()), IncomingFiles.route(intent))
    }

    @Test
    fun openingTheAppNormallyOrWithoutAFileStartsAtHome() {
        assertNull(IncomingFiles.route(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertNull(IncomingFiles.route(Intent(Intent.ACTION_SEND).setType("application/pdf")))
        assertNull(IncomingFiles.route(Intent(Intent.ACTION_VIEW)))
        assertNull(IncomingFiles.route(null))
    }

    @Test
    fun theMonthlyReviewNotificationOpensThatMonth() {
        val intent = Intent(Intent.ACTION_MAIN).putExtra(IncomingFiles.EXTRA_REVIEW_MONTH, "2026-09")
        assertEquals(Routes.MonthlyReview("2026-09"), IncomingFiles.route(intent))
    }
}
