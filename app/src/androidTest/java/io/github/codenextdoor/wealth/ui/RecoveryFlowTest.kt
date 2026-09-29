package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.db.DatabaseState
import org.junit.Test
import org.junit.runner.RunWith

/** The recovery screen's buttons (the test container never touches real files). */
@RunWith(AndroidJUnit4::class)
class RecoveryFlowTest : UiTest() {

    private fun showRecovery() {
        container.showRecoveryForTests(DatabaseState.Reason.KEY_UNAVAILABLE)
        waitForText("Your data can't be opened")
        waitForText("kept aside on the phone, not deleted", substring = true)
    }

    @Test
    fun tryAgainOpensTheAppWhenTheDataCanBeRead() {
        showRecovery()
        rule.onNodeWithText("Try again").performScrollTo().tap()
        waitForText("Overview")
    }

    @Test
    fun startFreshAsksFirst() {
        showRecovery()
        rule.onNodeWithText("Start fresh").performScrollTo().tap()
        waitForText("Start with an empty app?")
        rule.onNodeWithText("Cancel").tap()
        waitForText("Your data can't be opened") // nothing happened
        rule.onNodeWithText("Start fresh").performScrollTo().tap()
        waitForText("Start with an empty app?")
        rule.onAllNodesWithText("Start fresh").fetchSemanticsNodes().let { check(it.size == 2) }
        rule.onAllNodesWithText("Start fresh")[1].tap() // the dialog's button
        waitForText("Overview")
    }
}
