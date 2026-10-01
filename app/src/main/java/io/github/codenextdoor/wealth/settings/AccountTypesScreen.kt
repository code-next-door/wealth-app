package io.github.codenextdoor.wealth.settings

import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.domain.AccountType
import io.github.codenextdoor.wealth.domain.AssetKind
import io.github.codenextdoor.wealth.domain.Country
import io.github.codenextdoor.wealth.ui.components.BackTopBar
import io.github.codenextdoor.wealth.ui.components.ConfirmDeleteDialog
import io.github.codenextdoor.wealth.ui.components.DeleteBlockedDialog
import io.github.codenextdoor.wealth.ui.components.SectionHeader

@Composable
fun AccountTypesRoute(
    onBack: () -> Unit,
    viewModel: AccountTypesViewModel = viewModel(factory = AccountTypesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteBlocked by viewModel.deleteBlocked.collectAsStateWithLifecycle()
    AccountTypesScreen(
        state = state,
        deleteBlocked = deleteBlocked,
        onDismissDeleteBlocked = viewModel::dismissDeleteBlocked,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@Composable
fun AccountTypesScreen(
    state: AccountTypesUiState,
    deleteBlocked: String?,
    onDismissDeleteBlocked: () -> Unit,
    onBack: () -> Unit,
    onSave: (id: Long?, name: String, kind: AssetKind, countryId: Long?, holdsShares: Boolean, isLoan: Boolean) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val allTypes = state.sections.flatMap { it.types }
    val countryNames = state.countries.associate { it.id to it.name }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_account_types_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.account_type_add)) },
                text = { Text(stringResource(R.string.account_type_add)) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            state.sections.forEach { section ->
                item(key = section.group.toString()) {
                    SectionHeader(
                        when (val group = section.group) {
                            is AccountTypeGroup.InCountry -> group.countryName
                            AccountTypeGroup.General -> stringResource(R.string.account_types_section_general)
                            AccountTypeGroup.Liabilities -> stringResource(R.string.account_types_section_liabilities)
                        },
                    )
                }
                items(section.types, key = { it.id }) { type ->
                    ListItem(
                        headlineContent = { Text(type.name) },
                        // Liabilities are grouped together, so show their country here.
                        supportingContent = if (type.kind == AssetKind.LIABILITY) {
                            {
                                Text(
                                    type.countryId?.let(countryNames::get)
                                        ?: stringResource(R.string.account_types_section_general),
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { editingId = type.id },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAdd) {
        AccountTypeDialog(
            initial = null,
            countries = state.countries,
            onSave = { name, kind, countryId, holdsShares, isLoan -> onSave(null, name, kind, countryId, holdsShares, isLoan); showAdd = false },
            onDelete = null,
            onDismiss = { showAdd = false },
        )
    }

    editingId?.let { id ->
        val type = allTypes.firstOrNull { it.id == id }
        if (type == null) {
            editingId = null
        } else {
            AccountTypeDialog(
                initial = type,
                countries = state.countries,
                onSave = { name, kind, countryId, holdsShares, isLoan -> onSave(id, name, kind, countryId, holdsShares, isLoan); editingId = null },
                onDelete = { editingId = null; deletingId = id },
                onDismiss = { editingId = null },
            )
        }
    }

    deleteBlocked?.let { name ->
        DeleteBlockedDialog(
            itemName = name,
            message = stringResource(R.string.delete_blocked_type),
            onDismiss = onDismissDeleteBlocked,
        )
    }

    deletingId?.let { id ->
        ConfirmDeleteDialog(
            itemName = allTypes.firstOrNull { it.id == id }?.name.orEmpty(),
            message = stringResource(R.string.delete_message_generic),
            onConfirm = { onDelete(id); deletingId = null },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun AccountTypeDialog(
    initial: AccountType?,
    countries: List<Country>,
    onSave: (name: String, kind: AssetKind, countryId: Long?, holdsShares: Boolean, isLoan: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(initial?.name.orEmpty())
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: AssetKind.ASSET) }
    var holdsShares by rememberSaveable { mutableStateOf(initial?.holdsShares == true) }
    var isLoan by rememberSaveable { mutableStateOf(initial?.isLoan == true) }
    var countryId by rememberSaveable { mutableStateOf(initial?.countryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.account_type_add else R.string.account_type_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    state = name,
                    label = { Text(stringResource(R.string.name_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(R.string.account_type_kind),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AssetKind.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = kind == option,
                            onClick = { kind = option },
                            shape = SegmentedButtonDefaults.itemShape(index, AssetKind.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (option == AssetKind.ASSET) R.string.kind_asset else R.string.kind_liability,
                                ),
                            )
                        }
                    }
                }

                if (kind == AssetKind.ASSET) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .toggleable(value = holdsShares, role = Role.Switch, onValueChange = { holdsShares = it }),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.account_type_holds_shares), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.account_type_holds_shares_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = holdsShares, onCheckedChange = null)
                    }
                }
                if (kind == AssetKind.LIABILITY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .toggleable(value = isLoan, role = Role.Switch, onValueChange = { isLoan = it }),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.account_type_is_loan), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.account_type_is_loan_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isLoan, onCheckedChange = null)
                    }
                }

                Text(
                    stringResource(R.string.account_type_country),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                CountryOption(stringResource(R.string.country_general), countryId == null) { countryId = null }
                countries.forEach { country ->
                    CountryOption(country.name, countryId == country.id) { countryId = country.id }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.text.toString().trim(), kind, countryId, holdsShares && kind == AssetKind.ASSET, isLoan && kind == AssetKind.LIABILITY) },
                enabled = name.text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun CountryOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
