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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
 * Used for expense categories (with a switch, in an order you choose) and countries.
 * With [onReorder], each row has a drag handle, plus "Move up"/"Move down"
 * accessibility actions for screen readers (dragging doesn't work there).
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
    onReorder: ((ids: List<Long>) -> Unit)? = null,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    // The order on screen: rows move while dragging, and it's saved once the drag ends.
    var shown by remember { mutableStateOf(items) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) shown = items }
    fun moved(from: Int, to: Int): List<NamedItem> = shown.toMutableList().apply { add(to, removeAt(from)) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = shown.indexOfFirst { it.id == from.key }
        val toIndex = shown.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            shown = moved(fromIndex, toIndex)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

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
        LazyColumn(state = listState, contentPadding = padding) {
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
            itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
                ReorderableItem(reorderState, key = item.id, enabled = onReorder != null) { isDragging ->
                    val checked = item.checked
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "drag elevation")
                    fun moveTo(target: Int) {
                        shown = moved(index, target)
                        onReorder?.invoke(shown.map { it.id })
                    }
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = if (toggle != null && checked == false) {
                            { Text(toggle.offText) }
                        } else {
                            null
                        },
                        trailingContent = if ((toggle != null && checked != null) || onReorder != null) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (toggle != null && checked != null) {
                                        val description = stringResource(R.string.name_list_toggle_description, toggle.label, item.name)
                                        Switch(
                                            checked = checked,
                                            onCheckedChange = { toggle.onToggle(item.id, it) },
                                            modifier = Modifier.semantics { contentDescription = description },
                                        )
                                    }
                                    if (onReorder != null) {
                                        Icon(
                                            painterResource(R.drawable.ic_drag_indicator),
                                            contentDescription = stringResource(R.string.reorder_handle, item.name),
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        dragging = true
                                                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                    },
                                                    onDragStopped = {
                                                        dragging = false
                                                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                        onReorder(shown.map { it.id })
                                                    },
                                                )
                                                .size(48.dp)
                                                .padding(12.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        tonalElevation = elevation,
                        shadowElevation = elevation,
                        modifier = Modifier
                            .clickable { editingId = item.id }
                            .semantics {
                                if (onReorder != null) {
                                    customActions = listOfNotNull(
                                        if (index > 0) CustomAccessibilityAction(moveUp) { moveTo(index - 1); true } else null,
                                        if (index < shown.lastIndex) CustomAccessibilityAction(moveDown) { moveTo(index + 1); true } else null,
                                    )
                                }
                            },
                    )
                }
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
            onReorder = {},
        )
    }
}
