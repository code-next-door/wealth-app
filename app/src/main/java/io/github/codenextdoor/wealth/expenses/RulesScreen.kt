package io.github.codenextdoor.wealth.expenses

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.ExpenseCategory
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DropdownField
import io.github.codenextdoor.wealth.ui.components.DropdownOption

@Composable
fun RulesRoute(
    onBack: () -> Unit,
    viewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val state = viewModel.uiState(data)
    RulesScreen(
        state = state,
        onBack = onBack,
        testField = viewModel.testField,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onReapply = viewModel::reapply,
        onDismissReapplied = viewModel::dismissReapplied,
    )
}

// Dialog-only choices besides real category ids (which are always positive).
private const val SKIP_IMPORT = -1L
private const val NO_SELECTION = -2L

/** The keyword -> category dictionary used to categorize statement lines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: RulesUiState,
    onBack: () -> Unit,
    testField: TextFieldState,
    onSave: (id: Long?, keyword: String, categoryId: Long?) -> Unit,
    onDelete: (id: Long) -> Unit,
    onReapply: () -> Unit,
    onDismissReapplied: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmReapply by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmReapply = true }) {
                        Icon(painterResource(R.drawable.ic_sync), stringResource(R.string.rules_reapply))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.rules_add)) },
                text = { Text(stringResource(R.string.rules_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item {
                Text(
                    stringResource(R.string.rules_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            state = testField,
                            label = { Text(stringResource(R.string.rules_test_label)) },
                            placeholder = { Text("TWINT *COOP-4521 ZUERICH") },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.testText.isNotBlank()) {
                            Text(
                                state.testMatch?.let {
                                    stringResource(R.string.rules_test_match, it.categoryName ?: stringResource(R.string.rules_skip_import), it.keyword)
                                }
                                    ?: stringResource(R.string.rules_test_no_match),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.rules.forEach { rule ->
                        ListItem(
                            headlineContent = { Text(rule.keyword) },
                            trailingContent = {
                                Text(
                                    rule.categoryName ?: stringResource(R.string.rules_skip_import),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (rule.categoryId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (state.testMatch?.id == rule.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { editingId = rule.id },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        RuleDialog(
            title = stringResource(R.string.rules_add),
            initialKeyword = "",
            initialCategoryId = NO_SELECTION,
            categories = state.categories,
            onSave = { keyword, category -> onSave(null, keyword, category); adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
    editingId?.let { id ->
        val rule = state.rules.firstOrNull { it.id == id }
        if (rule == null) {
            editingId = null
        } else {
            RuleDialog(
                title = stringResource(R.string.rules_edit),
                initialKeyword = rule.keyword,
                initialCategoryId = rule.categoryId ?: SKIP_IMPORT,
                categories = state.categories,
                onSave = { keyword, category -> onSave(id, keyword, category); editingId = null },
                onDelete = { editingId = null; deletingId = id },
                onDismiss = { editingId = null },
            )
        }
    }
    deletingId?.let { id ->
        ConfirmDeleteDialog(
            itemName = state.rules.firstOrNull { it.id == id }?.keyword.orEmpty(),
            message = stringResource(R.string.rules_delete_message),
            onConfirm = { onDelete(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
    if (confirmReapply) {
        AlertDialog(
            onDismissRequest = { confirmReapply = false },
            title = { Text(stringResource(R.string.rules_reapply)) },
            text = { Text(stringResource(R.string.rules_reapply_message)) },
            confirmButton = { TextButton(onClick = { confirmReapply = false; onReapply() }) { Text(stringResource(R.string.rules_reapply_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmReapply = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.reappliedCount?.let { count ->
        AlertDialog(
            onDismissRequest = onDismissReapplied,
            text = { Text(pluralStringResource(R.plurals.rules_reapplied, count, count)) },
            confirmButton = { TextButton(onClick = onDismissReapplied) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun RuleDialog(
    title: String,
    initialKeyword: String,
    /** A category id, [SKIP_IMPORT], or [NO_SELECTION]. */
    initialCategoryId: Long,
    categories: List<ExpenseCategory>,
    onSave: (keyword: String, categoryId: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val keyword = rememberTextFieldState(initialKeyword)
    var categoryId by rememberSaveable { mutableStateOf(initialCategoryId) }
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
                DropdownField(
                    label = stringResource(R.string.expense_category_label),
                    options = categories.map { DropdownOption(it.id, it.name) } +
                        DropdownOption(SKIP_IMPORT, stringResource(R.string.rules_skip_import)),
                    selected = categoryId.takeIf { it != NO_SELECTION },
                    onSelect = { categoryId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (categoryId == SKIP_IMPORT) {
                    Text(
                        stringResource(R.string.rules_skip_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(keyword.text.toString(), categoryId.takeIf { it != SKIP_IMPORT }) },
                enabled = keyword.text.isNotBlank() && categoryId != NO_SELECTION,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
