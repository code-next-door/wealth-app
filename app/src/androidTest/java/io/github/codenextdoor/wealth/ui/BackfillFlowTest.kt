package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.domain.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Building history from statements, with the file picker stubbed. Invented statement only. */
@RunWith(AndroidJUnit4::class)
class BackfillFlowTest : UiTest() {

    private val files = mutableListOf<File>()

    @After
    fun deleteFiles() {
        files.forEach { it.delete() }
    }

    @Test
    fun aStatementFillsInItsAccountsHistory() {
        val name = "Backfill UBS ${System.nanoTime() % 10000}"
        val id = runBlocking {
            val type = container.catalogRepository.accountTypes.first().first { it.name == "Bank account" }
            container.accountRepository.save(
                Account(0, name, type.id, "CHF", type.countryId, 1_00, Instant.EPOCH, "UBS", null),
                // A fixed day before the statement: on its closing day (30.09.26) "today" would be replaced.
                balanceDate = LocalDate.of(2025, 1, 1),
                recordBalance = true,
            )
            container.accountRepository.accounts.first().first { it.name == name }.id
        }
        val pdf = cacheFile("fake-ubs-statement.pdf").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open("fake-ubs-statement.pdf")
            .use { input -> pdf.outputStream().use { input.copyTo(it) } }
        stubOpenDocument(pdf)

        rule.onNodeWithContentDescription("Settings").tap()
        rule.onNodeWithText("Build history from statements").performScrollTo().tap()
        waitForText("UBS account statement", substring = true)
        waitForText(name, substring = true) // the guessed account
        rule.onNode(hasText("Add ", substring = true) and hasText(" balance", substring = true) and hasClickAction()).tap()
        waitForText("to the history", substring = true)

        val history = runBlocking { container.accountRepository.observeHistory(id).first() }
        assertTrue("history: ${history.size}", history.size >= 2) // the account's first balance plus the statement's
        assertTrue(history.any { it.date == LocalDate.of(2026, 9, 30) }) // the statement's closing day
    }
}
