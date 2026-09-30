package io.github.codenextdoor.wealth.viewmodel

import io.github.codenextdoor.wealth.data.preferences.SettingsStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.codenextdoor.wealth.data.preferences.AppearancePreferences
import io.github.codenextdoor.wealth.data.preferences.ThemeMode
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.settings.AccountTypeGroup
import io.github.codenextdoor.wealth.settings.AccountTypesViewModel
import io.github.codenextdoor.wealth.settings.CategoriesViewModel
import io.github.codenextdoor.wealth.settings.CountriesViewModel
import io.github.codenextdoor.wealth.testutil.DatabaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewModelsTest : DatabaseTest() {

    @Test
    fun accountTypesAreGroupedByCountryThenGeneralThenLiabilities() {
        val vm = AccountTypesViewModel(catalog).cancelledAfterTest()
        val groups = vm.uiState.await { it.sections.isNotEmpty() }.sections.map { it.group }
        assertEquals(
            listOf(
                AccountTypeGroup.InCountry("Switzerland"),
                AccountTypeGroup.InCountry("India"),
                AccountTypeGroup.General,
                AccountTypeGroup.Liabilities,
            ),
            groups,
        )
    }

    @Test
    fun accountTypeSaveAndBlockedDelete() {
        val vm = AccountTypesViewModel(catalog).cancelledAfterTest()
        vm.uiState.await { it.sections.isNotEmpty() }
        vm.save(null, "  Crypto  ", AssetKind.ASSET, null)
        vm.save(null, "   ", AssetKind.ASSET, null) // blank names are ignored
        val general = vm.uiState.await { s -> s.sections.flatMap { it.types }.any { it.name == "Crypto" } }
            .sections.single { it.group == AccountTypeGroup.General }.types
        assertTrue(general.any { it.name == "Crypto" })

        addAccount("Salary", typeSeedKey = "ch_bank")
        vm.delete(typeId("ch_bank"))
        assertEquals("Bank account", vm.deleteBlocked.await { it != null })
        vm.dismissDeleteBlocked()
        assertEquals(null, vm.deleteBlocked.value)
    }

    @Test
    fun categoriesAndCountriesLists() {
        val categories = CategoriesViewModel(catalog).cancelledAfterTest()
        val before = categories.items.await { it.isNotEmpty() }.size
        categories.add("Pets")
        val pets = categories.items.await { it.size == before + 1 }.single { it.name == "Pets" }
        assertEquals(true, pets.checked) // a new category counts as spending
        categories.setCountsAsSpending(pets.id, false)
        categories.items.await { list -> list.single { it.id == pets.id }.checked == false }
        categories.rename(pets.id, "Pet care")
        categories.items.await { list -> list.any { it.name == "Pet care" } }
        categories.delete(pets.id)
        val order = categories.items.await { it.size == before }.map { it.id }
        categories.reorder(order.reversed())
        categories.items.await { list -> list.map { it.id } == order.reversed() }

        val countries = CountriesViewModel(catalog).cancelledAfterTest()
        countries.items.await { it.size == 2 }
        assertTrue(countries.items.value.all { it.checked == null }) // countries have no switch
        countries.add("Germany")
        assertTrue(countries.items.await { it.size == 3 }.any { it.name == "Germany" })
    }

    @Test
    fun categoriesComeInSpendingAndIncomeSections() {
        val categories = CategoriesViewModel(catalog).cancelledAfterTest()
        val items = categories.items.await { it.isNotEmpty() }
        val salary = items.single { it.name == "Salary" }
        assertEquals(CategoriesViewModel.INCOME, salary.group)
        assertNull(salary.checked) // income has no "counts as spending" switch
        assertEquals(CategoriesViewModel.SPENDING, items.single { it.name == "Groceries" }.group)

        categories.addInGroup("Bonus", CategoriesViewModel.INCOME)
        val bonus = categories.items.await { list -> list.any { it.name == "Bonus" } }.single { it.name == "Bonus" }
        assertEquals(CategoriesViewModel.INCOME, bonus.group)

        categories.setGroup(bonus.id, CategoriesViewModel.SPENDING) // changed its mind
        val moved = categories.items.await { list -> list.single { it.id == bonus.id }.group == CategoriesViewModel.SPENDING }
        assertEquals(true, moved.single { it.id == bonus.id }.checked) // a spending category counts by default
    }

    @Test
    fun appearanceIsRemembered() {
        val store = SettingsStore(context, "appearance_test")
        val prefs = AppearancePreferences(store)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.value)
        assertFalse(prefs.useWallpaperColors.value)
        assertFalse(prefs.figuresHidden.value) // figures shown by default
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setUseWallpaperColors(true)
        prefs.setFiguresHidden(true)
        store.close()
        val reopened = AppearancePreferences(SettingsStore(context, "appearance_test")) // read from disk
        assertEquals(ThemeMode.DARK, reopened.themeMode.value)
        assertTrue(reopened.useWallpaperColors.value)
        assertTrue(reopened.figuresHidden.value) // the eye stays closed after a restart
    }
}
