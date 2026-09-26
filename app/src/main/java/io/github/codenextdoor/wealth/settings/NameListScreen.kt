package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

data class NamedItem(val id: Long, val name: String)

/**
 * A simple editable list of names: tap to rename or delete, button to add.
 * Used for expense categories and countries.
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
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { BackTopBar(title, onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(addLabel) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
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
            items = listOf(NamedItem(1, "Groceries"), NamedItem(2, "Transport")),
            onBack = {}, onAdd = {}, onRename = { _, _ -> }, onDelete = {},
        )
    }
}
