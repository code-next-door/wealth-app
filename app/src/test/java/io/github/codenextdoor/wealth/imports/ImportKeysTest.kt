package io.github.codenextdoor.wealth.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ImportKeysTest {

    private val day = LocalDate.of(2026, 3, 1)
    private fun t(amount: String, text: String) = StatementTransaction(day, BigDecimal(amount), text)

    @Test
    fun sameStatementGivesSameKeys() {
        val rows = listOf(t("-4.50", "EXAMPLE CAFE"), t("-4.50", "EXAMPLE CAFE"), t("-10", "SHOP"))
        val keys = ImportKeys.forTransactions(rows, 7)
        assertEquals(keys, ImportKeys.forTransactions(rows, 7))
        assertEquals(3, keys.toSet().size) // the two equal coffees stay distinct
    }

    @Test
    fun keyIgnoresFormattingButNotAccount() {
        assertEquals(
            ImportKeys.forTransactions(listOf(t("-4.50", "Example  Café")), 1),
            ImportKeys.forTransactions(listOf(t("-4.5", "EXAMPLE CAFE")), 1),
        )
        assertNotEquals(
            ImportKeys.forTransactions(listOf(t("-4.50", "X")), 1),
            ImportKeys.forTransactions(listOf(t("-4.50", "X")), 2),
        )
    }
}
