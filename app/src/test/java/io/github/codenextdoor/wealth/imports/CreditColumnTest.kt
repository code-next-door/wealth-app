package io.github.codenextdoor.wealth.imports

import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Marking amounts by the column they're printed in, with invented page positions. */
@RunWith(AndroidJUnit4::class)
class CreditColumnTest {

    private val column = UbsCardTransactionsParser().creditColumn

    private val page = """
        Purchase Booking text Amount Debit Credit Booked
        05.01.2026 EXAMPLE SHOP
        CHF 50.00 06.01.2026
        07.01.2026 DIRECT DEBIT
        CHF 50.00 07.01.2026
        08.01.2026 FOREIGN SHOP
        USD 60.00 CHF 50.00 09.01.2026
    """.trimIndent()

    /** Where things are on the invented page: a box of (left, top, right, bottom). */
    private fun box(right: Float, top: Float) = RectF(right - 40, top, right, top + 10)

    private val positions = mapOf(
        column.header to listOf(listOf(box(500f, 100f))),
        "Debit" to listOf(listOf(box(440f, 100f)), listOf(box(200f, 300f))), // also "DIRECT DEBIT" lower down
        "Credit" to listOf(listOf(box(495f, 100f))),
        // Top to bottom: debit, credit, and the foreign row's francs in the Debit column.
        "CHF 50.00" to listOf(listOf(box(438f, 120f)), listOf(box(493f, 150f)), listOf(box(437f, 180f))),
    )

    @Test
    fun amountsUnderTheCreditHeaderAreMarked() {
        val marked = NativePdfText.CreditMarker(listOf(column)).mark(page) { positions[it].orEmpty() }.lines()
        assertEquals("CHF 50.00 06.01.2026", marked[2])
        assertEquals("CHF 50.00 07.01.2026${CreditColumn.MARK}", marked[4])
        assertEquals("USD 60.00 CHF 50.00 09.01.2026", marked[6])
    }

    @Test
    fun otherLayoutsAreLeftAlone() {
        val other = "Some statement\nCHF 50.00 06.01.2026"
        assertEquals(other, NativePdfText.CreditMarker(listOf(column)).mark(other) { positions[it].orEmpty() })
    }

    @Test
    fun laterPagesWithoutTheHeaderUseTheFirstPagesColumns() {
        val marker = NativePdfText.CreditMarker(listOf(column))
        marker.mark(page) { positions[it].orEmpty() }
        val nextPage = "09.01.2026 DIRECT DEBIT\nCHF 80.00 09.01.2026"
        val marked = marker.mark(nextPage) { if (it == "CHF 80.00") listOf(listOf(box(494f, 40f))) else emptyList() }
        assertEquals("CHF 80.00 09.01.2026${CreditColumn.MARK}", marked.lines()[1])
    }
}
