package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption
import io.github.codenextdoor.wealth.ui.components.TextInputDialog

/** The dropdown's last entry: not a category, it opens [AddCategoryDialog]. */
private const val ADD_CATEGORY = Long.MIN_VALUE

/**
 * Category dropdown: "Uncategorized", the categories, and "Others (add
 * manually)…", which asks for a name and hands it to [onAdd] (the caller adds
 * the category and selects it).
 */
@Composable
fun CategoryField(
    categories: List<ExpenseCategory>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onAdd: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    DropdownField(
        label = stringResource(R.string.expense_category_label),
        options = listOf(DropdownOption<Long?>(null, stringResource(R.string.expenses_uncategorized))) +
            categories.map { DropdownOption<Long?>(it.id, it.name) } +
            DropdownOption<Long?>(ADD_CATEGORY, stringResource(R.string.category_add_manually)),
        selected = selected,
        onSelect = { if (it == ADD_CATEGORY) adding = true else onSelect(it) },
        modifier = modifier,
    )
    if (adding) {
        AddCategoryDialog(onConfirm = { onAdd(it); adding = false }, onDismiss = { adding = false })
    }
}

/** Asks for a new category's name. */
@Composable
fun AddCategoryDialog(onConfirm: (name: String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        title = stringResource(R.string.category_add_manually_title),
        label = stringResource(R.string.name_label),
        confirmLabel = stringResource(R.string.action_add),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        header = {
            Text(
                stringResource(R.string.category_add_manually_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        },
    )
}
