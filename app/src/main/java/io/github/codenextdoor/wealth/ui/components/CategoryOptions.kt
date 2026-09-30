package io.github.codenextdoor.wealth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.ExpenseCategory

/**
 * A category picker's choices: "Uncategorized" (with [uncategorizedLabel]), then
 * spending categories, then income ones marked as such; each group in the order
 * chosen in settings.
 */
@Composable
fun categoryOptions(categories: List<ExpenseCategory>, uncategorizedLabel: String): List<DropdownOption<Long?>> {
    val (income, spending) = categories.partition { it.isIncome }
    return listOf(DropdownOption<Long?>(null, uncategorizedLabel)) +
        spending.map { DropdownOption<Long?>(it.id, it.name) } +
        income.map { DropdownOption<Long?>(it.id, stringResource(R.string.category_income_label, it.name)) }
}
