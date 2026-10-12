package io.github.codenextdoor.wealth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.codenextdoor.wealth.R
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
 * which way, so names need no marks; for money in, two headed groups: income, then the
 * spending categories as refunds (two same-named categories can then be told apart).
 */
@Composable
fun categoryOptions(categories: List<ExpenseCategory>, uncategorizedLabel: String, received: Boolean): List<DropdownOption<Long?>> {
    val choices = categoryChoices(categories, received)
    val incomeHeading = stringResource(R.string.category_group_income).takeIf { received }
    val refundHeading = stringResource(R.string.category_group_refund)
    return listOf(DropdownOption<Long?>(null, uncategorizedLabel, incomeHeading)) + choices.mapIndexed { i, choice ->
        val startsRefunds = choice.asRefund && (i == 0 || !choices[i - 1].asRefund)
        DropdownOption<Long?>(choice.category.id, choice.category.name, refundHeading.takeIf { startsRefunds })
    }
}

/** Every category, for rules (they match money out or in): spending ones, then income ones. */
fun ruleCategoryOptions(categories: List<ExpenseCategory>): List<DropdownOption<Long>> {
    val (income, spending) = categories.partition { it.isIncome }
    return (spending + income).map { DropdownOption(it.id, it.name) }
}
