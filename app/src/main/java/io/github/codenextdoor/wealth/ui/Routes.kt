package io.github.codenextdoor.wealth.ui

import kotlinx.serialization.Serializable

/** A screen's address, for [Routes]. */
sealed interface Route

/**
 * Every screen's address. Type-safe navigation: a screen's arguments are the
 * fields of its class, so the compiler checks them (no hand-built
 * "accounts/edit?accountId=5" strings). An edit screen's ViewModel reads its
 * id with `savedStateHandle.toRoute<Routes.XxxEdit>()`.
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
    @Serializable data object Recurring : Route

    /** No id adds a new one (same for the other edit screens). */
    @Serializable data class AccountEdit(val accountId: Long? = null) : Route
    @Serializable data class ExpenseEdit(val expenseId: Long? = null) : Route
    @Serializable data class GrantEdit(val grantId: Long? = null) : Route
    @Serializable data class HouseEdit(val accountId: Long? = null) : Route
    @Serializable data class RecurringEdit(val recurringId: Long? = null) : Route
}
