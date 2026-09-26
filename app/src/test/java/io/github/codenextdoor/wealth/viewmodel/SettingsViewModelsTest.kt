package io.github.codenextdoor.wealth.viewmodel

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewModelsTest : DatabaseTest() {

    @Test
    fun accountTypesAreGroupedByCountryThenGeneralThenLiabilities() {
        val vm = AccountTypesViewModel(catalog)
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
        val vm = AccountTypesViewModel(catalog)
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
        val categories = CategoriesViewModel(catalog)
        val before = categories.items.await { it.isNotEmpty() }.size
        categories.add("Pets")
        val pets = categories.items.await { it.size == before + 1 }.single { it.name == "Pets" }
        categories.rename(pets.id, "Pet care")
        categories.items.await { list -> list.any { it.name == "Pet care" } }
        categories.delete(pets.id)
        assertEquals(before, categories.items.await { it.size == before }.size)

        val countries = CountriesViewModel(catalog)
        countries.items.await { it.size == 2 }
        countries.add("Germany")
        assertTrue(countries.items.await { it.size == 3 }.any { it.name == "Germany" })
    }

    @Test
    fun appearanceIsRemembered() {
        val prefs = AppearancePreferences(context, prefsName = "appearance_test")
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.value)
        assertFalse(prefs.useWallpaperColors.value)
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setUseWallpaperColors(true)
        val reopened = AppearancePreferences(context, prefsName = "appearance_test")
        assertEquals(ThemeMode.DARK, reopened.themeMode.value)
        assertTrue(reopened.useWallpaperColors.value)
    }
}
