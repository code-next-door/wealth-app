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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import androidx.compose.material3.SnackbarHost
import io.github.codenextdoor.wealth.ui.LocalAppMessages
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.TextInputDialog
import io.github.codenextdoor.wealth.ui.theme.WealthTheme

/**
 * [checked] non-null shows a switch on the row (e.g. a category's "counts as spending");
 * [detail] is a line under the name (e.g. how many patterns a category has).
 */
data class NamedItem(val id: Long, val name: String, val checked: Boolean? = null, val group: Int = 0, val detail: String? = null)

/**
 * Optional sections (e.g. spending and income categories): [titles] by [NamedItem.group].
 * Adding and renaming then also choose the section; rows move within their section.
 */
data class NameListGroups(
    val titles: List<String>,
    val onAdd: (name: String, group: Int) -> Unit,
    val onChangeGroup: (id: Long, group: Int) -> Unit,
)

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
    groups: NameListGroups? = null,
    /** Tapping a row opens it (e.g. a category's own screen) instead of the rename dialog. */
    onOpen: ((id: Long) -> Unit)? = null,
    /** Shown above the list, under [intro] (e.g. a box to test a statement line). */
    header: (@Composable () -> Unit)? = null,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    // The order on screen: rows move while dragging, and it's saved once the drag ends.
    // With sections, each section's rows together (in their order); the saved order follows that.
    fun arranged(list: List<NamedItem>) = if (groups == null) list else list.sortedBy { it.group }
    var shown by remember { mutableStateOf(arranged(items)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) { if (!dragging) shown = arranged(items) }
    fun moved(from: Int, to: Int): List<NamedItem> = shown.toMutableList().apply { add(to, removeAt(from)) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = shown.indexOfFirst { it.id == from.key }
        val toIndex = shown.indexOfFirst { it.id == to.key }
        // Within a section only.
        if (fromIndex >= 0 && toIndex >= 0 && shown[fromIndex].group == shown[toIndex].group) {
            shown = moved(fromIndex, toIndex)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    Scaffold(
        topBar = { BackTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(LocalAppMessages.current.hostState) },
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
            header?.let { item { it() } }
            val sections = groups?.titles?.indices?.toList() ?: listOf(0)
            sections.forEach { section ->
                groups?.let {
                    item(key = "section-$section") {
                        Text(
                            it.titles[section],
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
            items(shown.filter { groups == null || it.group == section }, key = { it.id }) { item ->
                val index = shown.indexOf(item)
                // Neighbours within the section, for Move up/down.
                val sameSection = shown.filter { it.group == item.group }
                val position = sameSection.indexOf(item)
                ReorderableItem(reorderState, key = item.id, enabled = onReorder != null) { isDragging ->
                    val checked = item.checked
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "drag elevation")
                    fun moveTo(target: Int) {
                        shown = moved(index, target)
                        onReorder?.invoke(shown.map { it.id })
                    }
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = listOfNotNull(toggle?.offText?.takeIf { checked == false }, item.detail)
                            .takeIf { it.isNotEmpty() }
                            ?.let { lines -> { Text(lines.joinToString(" · ")) } },
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
                            .clickable { if (onOpen != null) onOpen(item.id) else editingId = item.id }
                            .semantics {
                                if (onReorder != null) {
                                    customActions = listOfNotNull(
                                        if (position > 0) {
                                            CustomAccessibilityAction(moveUp) { moveTo(shown.indexOf(sameSection[position - 1])); true }
                                        } else {
                                            null
                                        },
                                        if (position < sameSection.lastIndex) {
                                            CustomAccessibilityAction(moveDown) { moveTo(shown.indexOf(sameSection[position + 1])); true }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            },
                    )
                }
            }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAdd) {
        var group by rememberSaveable { mutableIntStateOf(0) }
        TextInputDialog(
            title = addLabel,
            label = stringResource(R.string.name_label),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = {
                if (groups != null) groups.onAdd(it, group) else onAdd(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
            header = groups?.let { g -> { GroupChoice(g.titles, group) { group = it } } },
        )
    }

    editingId?.let { id ->
        val item = items.firstOrNull { it.id == id }
        if (item == null) {
            editingId = null
        } else {
            var group by rememberSaveable(id) { mutableIntStateOf(item.group) }
            TextInputDialog(
                title = stringResource(R.string.rename_title),
                label = stringResource(R.string.name_label),
                confirmLabel = stringResource(R.string.action_save),
                initialValue = item.name,
                onConfirm = {
                    onRename(id, it)
                    if (groups != null && group != item.group) groups.onChangeGroup(id, group)
                    editingId = null
                },
                onDelete = { editingId = null; deletingId = id },
                onDismiss = { editingId = null },
                header = groups?.let { g -> { GroupChoice(g.titles, group) { group = it } } },
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

/** Which section a new or renamed row goes to (e.g. Spending / Income). */
@Composable
internal fun GroupChoice(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        titles.forEachIndexed { index, title ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = titles.size),
            ) { Text(title) }
        }
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
