package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.CategoryRule
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.TextInputDialog
import io.github.codenextdoor.wealth.ui.components.ruleCategoryOptions

@Composable
fun CategoryDetailRoute(
    categoryId: Long,
    onBack: () -> Unit,
    viewModel: CategoryDetailViewModel = viewModel(factory = CategoryDetailViewModel.factory(categoryId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReappliedMessage(state.reapplied, viewModel::reappliedShown)
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    CategoryDetailScreen(
        state = state,
        onBack = onBack,
        actions = CategoryDetailActions(
            onRename = viewModel::rename,
            onCountsAsSpending = viewModel::setCountsAsSpending,
            onDelete = viewModel::delete,
            onSavePattern = viewModel::savePattern,
            onDeletePattern = viewModel::deletePattern,
            ownerElsewhere = viewModel::ownerElsewhere,
        ),
    )
}

/** What the category screen can ask for. */
data class CategoryDetailActions(
    val onRename: (name: String, income: Boolean) -> Unit,
    val onCountsAsSpending: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    /** Adds (id null) or changes a pattern: its keyword and category. */
    val onSavePattern: (id: Long?, keyword: String, categoryId: Long) -> Unit,
    val onDeletePattern: (id: Long) -> Unit,
    /** The other category a keyword already belongs to, if any. */
    val ownerElsewhere: (keyword: String) -> ExpenseCategory?,
)

/** One category: its name, its switch and the patterns that sort statement lines into it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(state: CategoryDetailUiState, onBack: () -> Unit, actions: CategoryDetailActions) {
    val category = state.category
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { renaming = true }, enabled = category != null) {
                        Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.category_rename))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = category != null) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                    }
                },
            )
        },
        floatingActionButton = {
            val label = stringResource(R.string.pattern_add)
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = label) },
                text = { Text(label) },
            )
        },
        snackbarHost = { SnackbarHost(LocalAppMessages.current.hostState) },
    ) { padding ->
        if (category == null) return@Scaffold
        LazyColumn(contentPadding = padding) {
            // Spending categories can be left out of the totals (income has no such switch).
            if (!category.isIncome) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = category.countsAsSpending, role = Role.Switch, onValueChange = actions.onCountsAsSpending)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.category_counts_as_spending), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = category.countsAsSpending, onCheckedChange = null)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.patterns_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.patterns_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.patterns.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.patterns_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.patterns, key = { it.id }) { pattern ->
                ListItem(
                    headlineContent = { Text(pattern.keyword) },
                    modifier = Modifier.clickable { editingId = pattern.id },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (category == null) return

    if (renaming) {
        var group by rememberSaveable { mutableIntStateOf(if (category.isIncome) CategoriesViewModel.INCOME else CategoriesViewModel.SPENDING) }
        TextInputDialog(
            title = stringResource(R.string.rename_title),
            label = stringResource(R.string.name_label),
            confirmLabel = stringResource(R.string.action_save),
            initialValue = category.name,
            onConfirm = {
                actions.onRename(it, group == CategoriesViewModel.INCOME)
                renaming = false
            },
            onDismiss = { renaming = false },
            header = {
                GroupChoice(listOf(stringResource(R.string.category_group_spending), stringResource(R.string.category_group_income)), group) { group = it }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = category.name,
            message = stringResource(R.string.category_delete_message),
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }

    if (adding) {
        PatternDialog(
            title = stringResource(R.string.pattern_add),
            pattern = null,
            category = category,
            categories = state.categories,
            ownerElsewhere = actions.ownerElsewhere,
            onSave = { keyword, to -> actions.onSavePattern(null, keyword, to); adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editingId?.let { id ->
        val pattern = state.patterns.firstOrNull { it.id == id }
        if (pattern == null) {
            editingId = null
        } else {
            PatternDialog(
                title = stringResource(R.string.pattern_edit),
                pattern = pattern,
                category = category,
                categories = state.categories,
                ownerElsewhere = actions.ownerElsewhere,
                onSave = { keyword, to -> actions.onSavePattern(id, keyword, to); editingId = null },
                onDelete = { actions.onDeletePattern(id); editingId = null },
                onDismiss = { editingId = null },
            )
        }
    }
}

/**
 * A pattern's keyword and the category it sorts into (here by default; another moves it).
 * Says so when the keyword already belongs to another category: saving moves it here.
 */
@Composable
private fun PatternDialog(
    title: String,
    pattern: CategoryRule?,
    category: ExpenseCategory,
    categories: List<ExpenseCategory>,
    ownerElsewhere: (String) -> ExpenseCategory?,
    onSave: (keyword: String, categoryId: Long) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val keyword = rememberTextFieldState(pattern?.keyword.orEmpty())
    var target by rememberSaveable { mutableLongStateOf(category.id) }
    val elsewhere = keyword.text.toString().takeIf { it.isNotBlank() && it != pattern?.keyword }?.let(ownerElsewhere)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    state = keyword,
                    label = { Text(stringResource(R.string.rule_keyword_label)) },
                    supportingText = { Text(stringResource(R.string.rule_keyword_hint)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                elsewhere?.let {
                    Text(
                        stringResource(R.string.pattern_elsewhere, keyword.text.toString().trim().uppercase(), it.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                // Moving a pattern: only when editing (a new one belongs here).
                if (pattern != null) {
                    DropdownField(
                        label = stringResource(R.string.pattern_category_label),
                        options = ruleCategoryOptions(categories),
                        selected = target,
                        onSelect = { target = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(keyword.text.toString(), target) }, enabled = keyword.text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
