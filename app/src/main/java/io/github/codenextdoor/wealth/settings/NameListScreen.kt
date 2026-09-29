package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.TextInputDialog
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

/** [checked] non-null shows a switch on the row (e.g. a category's "counts as spending"). */
data class NamedItem(val id: Long, val name: String, val checked: Boolean? = null)

/** The optional switch on each row: its label, and the row's text when switched off. */
data class NameListToggle(
    val label: String,
    val offText: String,
    val onToggle: (id: Long, checked: Boolean) -> Unit,
)

/**
 * A simple editable list of names: tap to rename or delete, button to add.
 * Used for expense categories (with a switch) and countries.
 */
@Composable
fun NameListScreen(
    title: String,
    addLabel: String,
    deleteMessage: String,
    items: List<NamedItem>,
    onBack: () -> Unit,
    onAdd: (name: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    intro: String? = null,
    toggle: NameListToggle? = null,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { BackTopBar(title, onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = addLabel) },
                text = { Text(addLabel) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            intro?.let {
                item {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                val checked = item.checked
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = if (toggle != null && checked == false) {
                        { Text(toggle.offText) }
                    } else {
                        null
                    },
                    trailingContent = if (toggle != null && checked != null) {
                        {
                            val description = stringResource(R.string.name_list_toggle_description, toggle.label, item.name)
                            Switch(
                                checked = checked,
                                onCheckedChange = { toggle.onToggle(item.id, it) },
                                modifier = Modifier.semantics { contentDescription = description },
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { editingId = item.id },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = addLabel,
            label = stringResource(R.string.name_label),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { onAdd(it); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }

    editingId?.let { id ->
        val item = items.firstOrNull { it.id == id }
        if (item == null) {
            editingId = null
        } else {
            TextInputDialog(
                title = stringResource(R.string.rename_title),
                label = stringResource(R.string.name_label),
                confirmLabel = stringResource(R.string.action_save),
                initialValue = item.name,
                onConfirm = { onRename(id, it); editingId = null },
                onDelete = { editingId = null; deletingId = id },
                onDismiss = { editingId = null },
            )
        }
    }

    deletingId?.let { id ->
        ConfirmDeleteDialog(
            itemName = items.firstOrNull { it.id == id }?.name.orEmpty(),
            message = deleteMessage,
            onConfirm = { onDelete(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NameListScreenPreview() {
    WealthTheme {
        NameListScreen(
            title = "Expense categories",
            addLabel = "Add category",
            deleteMessage = "",
            items = listOf(NamedItem(1, "Groceries", true), NamedItem(2, "Investments & transfers", false)),
            onBack = {}, onAdd = {}, onRename = { _, _ -> }, onDelete = {},
            intro = "Switch off categories that aren't spending.",
            toggle = NameListToggle("Counts as spending", "Not counted as spending") { _, _ -> },
        )
    }
}
