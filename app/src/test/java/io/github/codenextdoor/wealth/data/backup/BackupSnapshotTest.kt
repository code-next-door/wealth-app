package io.github.codenextdoor.wealth.data.backup

import io.github.codenextdoor.wealth.data.db.AccountEntity
import io.github.codenextdoor.wealth.data.db.AccountTypeEntity
import io.github.codenextdoor.wealth.data.db.BalanceEntryEntity
import io.github.codenextdoor.wealth.data.db.CategoryRuleEntity
import io.github.codenextdoor.wealth.data.db.CountryEntity
import io.github.codenextdoor.wealth.data.db.CurrencyEntity
import io.github.codenextdoor.wealth.data.db.ExchangeRateEntity
import io.github.codenextdoor.wealth.data.db.ExpenseCategoryEntity
import io.github.codenextdoor.wealth.data.db.ExpenseEntity
import io.github.codenextdoor.wealth.data.db.SettingEntity
import io.github.codenextdoor.wealth.domain.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSnapshotTest {

    private val snapshot = BackupSnapshot(
        createdAt = 1_790_000_000_000,
        currencies = listOf(CurrencyEntity("CHF", "Swiss Franc", 2, 0), CurrencyEntity("INR", "Indian Rupee", 2, 1)),
        exchangeRates = listOf(ExchangeRateEntity(1, "CHF", "INR", 20_000, "105.5")),
        countries = listOf(CountryEntity(1, "ch", "Switzerland", 0), CountryEntity(2, null, "Mars", 1)),
        accountTypes = listOf(AccountTypeEntity(3, "ch_bank", "Bank account", AssetKind.ASSET, 1, 0), AccountTypeEntity(4, null, "Loan", AssetKind.LIABILITY, null, 1)),
        expenseCategories = listOf(ExpenseCategoryEntity(5, "groceries", "Groceries", 0)),
        categoryRules = listOf(CategoryRuleEntity(6, "COOP", 5), CategoryRuleEntity(7, "CARD CENTER", null)),
        accounts = listOf(AccountEntity(8, "Salary", 3, "CHF", 1, 123_45, 1_000, "Example Bank", null)),
        balanceEntries = listOf(BalanceEntryEntity(9, 8, 20_000, 123_45)),
        expenses = listOf(
            ExpenseEntity(10, 20_001, 45_30, "CHF", "COOP ZURICH", 5, false, 8, "note with \"quotes\" and émojis ✓", 5_000, "key|1"),
            ExpenseEntity(11, 20_002, -5_00, "INR", "Refund", null, true, null, null, 6_000, null),
        ),
        settings = listOf(SettingEntity("base_currency", "CHF")),
    )

    @Test
    fun roundTripsEverythingIncludingNulls() {
        assertEquals(snapshot, BackupSnapshot.fromJson(snapshot.toJson()))
    }

    @Test(expected = BackupSnapshot.Companion.UnsupportedBackup::class)
    fun rejectsNewerFormat() {
        BackupSnapshot.fromJson(snapshot.toJson().replace("\"format\":1", "\"format\":99"))
    }

    @Test(expected = BackupSnapshot.Companion.UnsupportedBackup::class)
    fun rejectsOtherApps() {
        BackupSnapshot.fromJson("""{"app":"something.else","format":1,"createdAt":0}""")
    }
}
