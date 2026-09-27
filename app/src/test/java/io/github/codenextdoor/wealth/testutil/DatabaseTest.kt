package io.github.codenextdoor.wealth.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.data.repository.AccountRepository
import io.github.codenextdoor.wealth.data.repository.CatalogRepository
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import io.github.codenextdoor.wealth.data.repository.ExpenseRepository
import io.github.codenextdoor.wealth.data.seed.DatabaseSeeder
import io.github.codenextdoor.wealth.domain.Account
import io.github.codenextdoor.wealth.domain.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Base for integration tests: a real Room database (in memory, via
 * Robolectric), seeded with the default data, and the app's repositories.
 * Tests run in real time (runBlocking) because Room delivers results on its
 * own threads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class DatabaseTest {

    protected lateinit var context: Context
    protected lateinit var db: WealthDatabase
    protected val currencies by lazy { CurrencyRepository(db) }
    protected val catalog by lazy { CatalogRepository(db) }
    protected val accounts by lazy { AccountRepository(db) }
    protected val expenses by lazy { ExpenseRepository(db) }

    protected val today: LocalDate = LocalDate.now()

    @Before
    fun openDatabase() {
        // ViewModels launch on Main; run those coroutines straight away.
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WealthDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { DatabaseSeeder(db, context).seedIfNeeded() }
    }

    @After
    fun closeDatabase() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * Waits (up to 15 s, real time; shared CI machines can be slow) for a value
     * matching [predicate]. Returns as soon as there is one.
     */
    protected fun <T> Flow<T>.await(predicate: (T) -> Boolean = { true }): T =
        runBlocking { withTimeout(WAIT_MS) { first(predicate) } }

    /**
     * Polls until [condition] holds (up to 15 s). For state that isn't a single
     * Flow, e.g. form text held in Compose state and updated from a coroutine.
     */
    protected fun eventually(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within ${WAIT_MS / 1000} s" }
            Thread.sleep(20)
        }
    }

    protected fun typeId(seedKey: String): Long = runBlocking {
        db.accountTypeDao().observeAll().first().first { it.seedKey == seedKey }.id
    }

    protected fun categoryId(seedKey: String): Long = runBlocking {
        db.expenseCategoryDao().getAll().first { it.seedKey == seedKey }.id
    }

    protected fun countryId(seedKey: String): Long = runBlocking {
        db.countryDao().observeAll().first().first { it.seedKey == seedKey }.id
    }

    /** Adds an account with a first balance on [date]; returns its id. */
    protected fun addAccount(
        name: String,
        typeSeedKey: String = "ch_bank",
        currency: String = "CHF",
        balanceMinor: Long = 100_00,
        date: LocalDate = today,
        countrySeedKey: String? = "ch",
        institution: String? = null,
    ): Long = runBlocking {
        accounts.save(
            Account(0, name, typeId(typeSeedKey), currency, countrySeedKey?.let { countryId(it) }, balanceMinor, Instant.EPOCH, institution, null),
            balanceDate = date,
            recordBalance = true,
        )
        accounts.accounts.first().first { it.name == name }.id
    }

    protected fun setRate(from: String, to: String, rate: String, date: LocalDate = today) = runBlocking {
        currencies.setRate(from, to, BigDecimal(rate), date)
    }

    protected fun expense(
        description: String,
        amountMinor: Long,
        date: LocalDate = today,
        currency: String = "CHF",
        categoryId: Long? = null,
        locked: Boolean = false,
        accountId: Long? = null,
    ) = Expense(0, date, amountMinor, currency, description, categoryId, locked, accountId, null)

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
