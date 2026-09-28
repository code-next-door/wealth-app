package io.github.codenextdoor.wealth.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.codenextdoor.wealth.data.db.Migrations
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a database with real data from the first version to the latest,
 * one step at a time, checking after each step that Room's expected schema
 * matches and that no data was lost. This is what protects users' data when
 * they update the app. Runs on the JVM (Robolectric, real SQLite), so CI
 * checks it on every push.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WealthDatabase::class.java)

    @After
    fun deleteTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun upgradesFromVersion1ToLatestKeepingData() {
        val dayMillis = 86_400_000L
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO currencies (code, name, decimals, sortOrder) VALUES ('CHF','Swiss Franc',2,0), ('INR','Indian Rupee',2,1)")
            execSQL("INSERT INTO exchange_rates (fromCode, toCode, rate, updatedAt) VALUES ('CHF','INR','105',${20_000 * dayMillis + 5_000})")
            execSQL("INSERT INTO countries (id, seedKey, name, sortOrder) VALUES (1,'ch','Switzerland',0)")
            execSQL("INSERT INTO account_types (id, seedKey, name, kind, countryId, sortOrder) VALUES (1,'ch_bank','Bank account','ASSET',1,0)")
            execSQL("INSERT INTO expense_categories (id, seedKey, name, sortOrder) VALUES (1,'groceries','Groceries',0)")
            execSQL("INSERT INTO settings (name, value) VALUES ('base_currency','CHF'), ('seed_version','1')")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true).apply {
            execSQL(
                "INSERT INTO accounts (id, name, accountTypeId, currencyCode, countryId, balanceMinor, balanceUpdatedAt, institution, note) " +
                    "VALUES (1,'Salary',1,'CHF',1,1234500,${20_010 * dayMillis},'UBS',NULL)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true, *Migrations.ALL).apply {
            // Each account's balance became its first history entry, on the right day.
            query("SELECT accountId, date, balanceMinor FROM balance_entries").use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
                assertEquals(20_010L, it.getLong(1))
                assertEquals(1_234_500L, it.getLong(2))
                assertEquals(1, it.count)
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL).apply {
            // The single current rate became a dated history entry.
            query("SELECT fromCode, toCode, date, rate FROM exchange_rate_history").use {
                assertTrue(it.moveToFirst())
                assertEquals("CHF", it.getString(0))
                assertEquals("INR", it.getString(1))
                assertEquals(20_000L, it.getLong(2))
                assertEquals("105", it.getString(3))
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 5, true, *Migrations.ALL).apply {
            execSQL("INSERT INTO category_rules (id, keyword, categoryId) VALUES (1,'MIGROS',1)")
            execSQL(
                "INSERT INTO expenses (id, date, amountMinor, currencyCode, description, categoryId, categoryLocked, accountId, note, createdAt) " +
                    "VALUES (1,20011,4530,'CHF','MIGROS',1,0,1,NULL,0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true, *Migrations.ALL).apply {
            query("SELECT keyword, categoryId FROM category_rules").use {
                assertTrue(it.moveToFirst())
                assertEquals("MIGROS", it.getString(0))
                assertEquals(1L, it.getLong(1))
            }
            // Rules can now mean "don't import" (no category).
            execSQL("INSERT INTO category_rules (keyword, categoryId) VALUES ('CARD CENTER', NULL)")
            query("SELECT description, importKey FROM expenses").use {
                assertTrue(it.moveToFirst())
                assertEquals("MIGROS", it.getString(0))
                assertTrue(it.isNull(1))
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 7, true, *Migrations.ALL).apply {
            // Rates from before downloading existed were all typed by the user.
            query("SELECT rate, source FROM exchange_rate_history").use {
                assertTrue(it.moveToFirst())
                assertEquals("105", it.getString(0))
                assertEquals("manual", it.getString(1))
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 8, true, *Migrations.ALL).apply {
            // Existing accounts and types don't hold shares; the new tables work.
            query("SELECT shareSymbol, units FROM accounts").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0) && it.isNull(1))
            }
            query("SELECT holdsShares FROM account_types").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            execSQL("INSERT INTO share_prices (symbol, date, price) VALUES ('GOOG', 20100, '150')")
            execSQL(
                "INSERT INTO grants (name, symbol, currencyCode, grantDate, totalUnits, vestStart, vestMonths, intervalMonths, cliffMonths, note) " +
                    "VALUES ('Grant', 'GOOG', 'CHF', 20000, '48', 20000, 48, 1, 0, NULL)",
            )
            query("SELECT source FROM share_prices").use {
                assertTrue(it.moveToFirst())
                assertEquals("manual", it.getString(0))
            }
            close()
        }

        helper.runMigrationsAndValidate(dbName, 9, true, *Migrations.ALL).apply {
            // Existing expenses keep their data and weren't added by a recurring expense.
            query("SELECT description, importKey, recurringId FROM expenses").use {
                assertTrue(it.moveToFirst())
                assertEquals("MIGROS", it.getString(0))
                assertTrue(it.isNull(2))
            }
            execSQL(
                "INSERT INTO recurring_expenses (id, description, amountMinor, currencyCode, categoryId, accountId, intervalMonths, startDate, endDate, lastAdded) " +
                    "VALUES (1, 'Rent', 200000, 'CHF', 1, 1, 1, 20000, NULL, NULL)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 10, true, *Migrations.ALL).apply {
            // Only a new table: the account, its history and expenses are untouched.
            query("SELECT name, balanceMinor FROM accounts").use {
                assertTrue(it.moveToFirst())
                assertEquals("Salary", it.getString(0))
                assertEquals(1_234_500L, it.getLong(1))
            }
            query("SELECT COUNT(*) FROM expenses").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            execSQL("INSERT INTO properties (accountId, purchasePriceMinor, purchaseDate, growthPercent, loanAccountId) VALUES (1, 100, 18000, '7', NULL)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 11, true, *Migrations.ALL).apply {
            // Existing accounts stay in net worth; nothing else changes.
            query("SELECT name, balanceMinor, excludedFromNetWorth FROM accounts").use {
                assertTrue(it.moveToFirst())
                assertEquals("Salary", it.getString(0))
                assertEquals(1_234_500L, it.getLong(1))
                assertEquals(0, it.getInt(2))
            }
            query("SELECT COUNT(*) FROM properties").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            close()
        }
    }
}
