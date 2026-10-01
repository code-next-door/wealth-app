package io.github.codenextdoor.wealth.ui.components

import androidx.compose.runtime.Composable
import io.github.codenextdoor.wealth.domain.ExpenseCategory

/** A category a picker offers; [asRefund] for a spending category offered for money in. */
data class CategoryChoice(val category: ExpenseCategory, val asRefund: Boolean = false)

/**
 * The categories that fit one direction, each group in the order chosen in settings:
 * money out → the spending categories (counted or not); money in → the income ones, then
 * the spending ones as refunds (money back lowers that category's spending).
 */
fun categoryChoices(categories: List<ExpenseCategory>, received: Boolean): List<CategoryChoice> {
    val (income, spending) = categories.partition { it.isIncome }
    return if (received) income.map { CategoryChoice(it) } + spending.map { CategoryChoice(it, asRefund = true) } else spending.map { CategoryChoice(it) }
}

/** Whether [categoryId] can be picked for that direction (none, "uncategorized", always can). */
fun fitsDirection(categories: List<ExpenseCategory>, categoryId: Long?, received: Boolean): Boolean =
    categoryId == null || categoryChoices(categories, received).any { it.category.id == categoryId }

/**
 * A picker's choices for one direction (see [categoryChoices]): "Uncategorized" (with
 * [uncategorizedLabel]), then the categories by name. Spent / Received in the form says
 * which way, so names need no marks; for money in, spending categories come last (refunds).
 */
@Composable
fun categoryOptions(categories: List<ExpenseCategory>, uncategorizedLabel: String, received: Boolean): List<DropdownOption<Long?>> =
    listOf(DropdownOption<Long?>(null, uncategorizedLabel)) + categoryChoices(categories, received).map { DropdownOption<Long?>(it.category.id, it.category.name) }

/** Every category, for rules (they match money out or in): spending ones, then income ones. */
fun ruleCategoryOptions(categories: List<ExpenseCategory>): List<DropdownOption<Long>> {
    val (income, spending) = categories.partition { it.isIncome }
    return (spending + income).map { DropdownOption(it.id, it.name) }
}
