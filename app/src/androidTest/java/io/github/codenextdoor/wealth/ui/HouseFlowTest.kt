package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The House tab through the real UI. */
@RunWith(AndroidJUnit4::class)
class HouseFlowTest : UiTest() {

    @After
    fun countHousesAgain() {
        runBlocking { container.houseRepository.setInNetWorth(true) }
    }

    @Test
    fun addAHouseAndLeaveItOutOfNetWorth() {
        addBankAccount("House test bank ${System.nanoTime() % 10000}") // so the Overview shows its headline
        val name = "Test flat ${System.nanoTime() % 10000}"
        openTab("House")
        rule.onNodeWithContentDescription("Add house").tap()
        typeInto("Name, e.g.", name, substring = true)
        typeInto("Purchase price", "500000")
        typeInto("Expected growth per year", "5")
        rule.onNodeWithText("Save").performScrollTo().tap()

        waitForText(name)
        waitForText("Bought for", substring = true)
        rule.onNodeWithText("Count houses in net worth").tap()
        runBlocking { check(!container.houseRepository.inNetWorth.first()) { "switch didn't turn off" } }
        openTab("Overview")
        waitForText("not in net worth", substring = true)
    }
}
