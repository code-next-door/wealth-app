package io.github.codenextdoor.wealth.ui

import androidx.navigation3.runtime.NavKey
import io.github.codenextdoor.wealth.domain.AssetKind
import kotlinx.serialization.Serializable

/** A screen's address, for [Routes]. */
sealed interface Route : NavKey

/**
 * Every screen's address (Navigation 3 keys). A screen's arguments are the
 * fields of its class, so the compiler checks them; the back stack is a list
 * of these (saved across process death, hence @Serializable). An edit screen
 * gets its id straight from its key.
 */
object Routes {
    @Serializable data object Home : Route
    @Serializable data object Settings : Route
    @Serializable data object Currencies : Route
    @Serializable data object AccountTypes : Route
    @Serializable data object Categories : Route
    @Serializable data object Countries : Route
    @Serializable data object Rules : Route
    @Serializable data object History : Route
    @Serializable data object Import : Route
    @Serializable data object Backfill : Route
    /** Recurring expenses were removed; kept so a saved screen from before still opens (it goes back). */
    @Serializable data object Recurring : Route

    /** No id adds a new one (same for the other edit screens). */
    /** [kind]: added from the Assets or Liabilities section (only that kind's types). */
    @Serializable data class AccountEdit(val accountId: Long? = null, val kind: AssetKind? = null) : Route
    @Serializable data class ExpenseEdit(val expenseId: Long? = null) : Route
    @Serializable data class GrantEdit(val grantId: Long? = null) : Route
    @Serializable data class HouseEdit(val accountId: Long? = null) : Route
    /** Removed like [Recurring]; kept for the same reason. */
    @Serializable data class RecurringEdit(val recurringId: Long? = null) : Route
}
