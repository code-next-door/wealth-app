package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.TextInputDialog
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

/** [switchedOn] is the item's switch (see [NameListSwitch]); null when the list has none. */
data class NamedItem(val id: Long, val name: String, val switchedOn: Boolean? = null)

/** An on/off setting shown on every item, e.g. whether a category counts as spending. */
class NameListSwitch(
    /** Explains the switch, above the list. */
    val intro: String,
    val onLabel: String,
    val offLabel: String,
    val onChange: (id: Long, on: Boolean) -> Unit,
)

/**
 * A simple editable list of names: tap to rename or delete, button to add.
 * Used for expense categories (with a [switch]) and countries.
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
    switch: NameListSwitch? = null,
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
            switch?.let {
                item {
                    Text(
                        it.intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                val on = item.switchedOn
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = if (switch != null && on != null) {
                        { Text(if (on) switch.onLabel else switch.offLabel) }
                    } else {
                        null
                    },
                    trailingContent = if (switch != null && on != null) {
                        {
                            Switch(
                                checked = on,
                                onCheckedChange = { switch.onChange(item.id, it) },
                                modifier = Modifier.semantics { contentDescription = "${item.name}: ${switch.onLabel}" },
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
            items = listOf(NamedItem(1, "Groceries"), NamedItem(2, "Transport")),
            onBack = {}, onAdd = {}, onRename = { _, _ -> }, onDelete = {},
        )
    }
}
