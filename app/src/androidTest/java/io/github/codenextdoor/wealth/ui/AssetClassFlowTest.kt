package io.github.codenextdoor.wealth.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.domain.AssetClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Asset classes: the Overview's Class view, and choosing a type's class in Settings. */
@RunWith(AndroidJUnit4::class)
class AssetClassFlowTest : UiTest() {

    private fun goldType() = runBlocking { container.catalogRepository.accountTypes.first().single { it.seedKey == "gold" } }

    @After
    fun putGoldBack() = runBlocking {
        val gold = goldType()
        container.catalogRepository.updateAccountType(gold.id, gold.name, gold.kind, gold.countryId, assetClass = AssetClass.GOLD)
    }

    @Test
    fun theOverviewGroupsAssetsByClass() {
        addBankAccount("Class test ${System.nanoTime() % 10000}")
        openTab("Overview")
        val list = rule.onNode(hasScrollToNodeAction())
        val classChip = hasText("Class") and hasClickAction()
        list.performScrollToNode(classChip)
        rule.onNode(classChip).tap()
        list.performScrollToNode(hasText("Cash & deposits"))
        waitForText("Cash & deposits")
    }

    @Test
    fun aTypesClassIsChosenInItsEditor() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Account types").performScrollTo().tap()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(goldType().name))
        rule.onNodeWithText(goldType().name).tap()
        waitForText("Asset class")
        rule.onNodeWithText("Asset class").performClick() // opens its dropdown, like other forms' fields
        rule.onNodeWithText("Stocks & funds").tap()
        rule.onNodeWithText("Save").tap()
        rule.waitUntil(5_000) { goldType().assetClass == AssetClass.STOCKS }
    }
}
