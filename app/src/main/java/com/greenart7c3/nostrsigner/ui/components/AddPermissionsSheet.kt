package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.add_permission_description
import com.greenart7c3.nostrsigner.commonsui.custom_event_kind
import com.greenart7c3.nostrsigner.commonsui.custom_event_kind_already_added
import com.greenart7c3.nostrsigner.commonsui.go_back
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.no_permissions_found
import com.greenart7c3.nostrsigner.commonsui.search_permissions
import com.greenart7c3.nostrsigner.commonsui.supportedKindNumbers
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import org.jetbrains.compose.resources.stringResource

private fun permissionKey(permission: Permission) = "${permission.type}-${permission.kind}"

private fun availablePermissions(existingPermissions: List<Permission>) = supportedKindNumbers
    .distinctBy { permissionKey(it) }
    .filterNot { candidate ->
        existingPermissions.any {
            it.type.equals(candidate.type, ignoreCase = true) && it.kind == candidate.kind
        }
    }

private fun matchingPermissions(permissions: List<Permission>, search: String) = if (search.isBlank()) {
    permissions
} else {
    permissions.filter {
        it.localizedBlocking(true).contains(search, ignoreCase = true) ||
            it.kind.toString().contains(search) ||
            it.type.contains(search, ignoreCase = true)
    }
}

private data class CustomKindInput(
    val text: String,
    val kind: Int?,
    val alreadyAdded: Boolean,
    val addEnabled: Boolean,
)

private fun customKindInput(
    text: String,
    existingPermissions: List<Permission>,
    customPermissions: List<Permission>,
): CustomKindInput {
    val kind = text.toIntOrNull()
    val alreadyAdded = kind != null &&
        existingPermissions.any {
            it.type.equals("sign_event", ignoreCase = true) && it.kind == kind
        }
    val addEnabled = kind != null &&
        !alreadyAdded &&
        customPermissions.none { it.kind == kind }
    return CustomKindInput(text, kind, alreadyAdded, addEnabled)
}

/**
 * Bottom sheet that lets the user pick permissions from the full supported list to
 * be approved automatically, excluding the ones the application already has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPermissionsSheet(
    existingPermissions: List<Permission>,
    onAdd: (List<Permission>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val candidates = remember(existingPermissions) { availablePermissions(existingPermissions) }

    val selectedPermissions = remember { mutableStateMapOf<String, Boolean>() }
    var search by remember { mutableStateOf("") }

    // Custom sign_event kinds typed by the user, added on top of the catalog list.
    val customPermissions = remember { mutableStateListOf<Permission>() }
    var customKindText by remember { mutableStateOf("") }
    val customKind = customKindInput(customKindText, existingPermissions, customPermissions)

    val customCandidates = customPermissions.toList()
    val allCandidates = customCandidates + candidates
    val filteredPermissions = remember(allCandidates, search) { matchingPermissions(allCandidates, search) }

    val hasSelection = selectedPermissions.values.any { it }

    ModalBottomSheet(

        modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Scaffold(
            bottomBar = {
                AddPermissionsBottomBar(
                    addEnabled = hasSelection,
                    onAdd = {
                        onAdd(allCandidates.filter { selectedPermissions[permissionKey(it)] == true })
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                )
            },
        ) { padding ->
            AddPermissionsForm(
                search = search,
                onSearchChange = { search = it },
                customKind = customKind,
                onCustomKindChange = { customKindText = it.filter { char -> char.isDigit() } },
                onAddCustomKind = {
                    val kind = customKind.kind ?: return@AddPermissionsForm
                    val permission = Permission("sign_event", kind)
                    customPermissions.add(permission)
                    selectedPermissions[permissionKey(permission)] = true
                    customKindText = ""
                },
                permissions = filteredPermissions,
                isSelected = { selectedPermissions[permissionKey(it)] ?: false },
                onSelectChange = { permission, selected -> selectedPermissions[permissionKey(permission)] = selected },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AddPermissionsForm(
    search: String,
    onSearchChange: (String) -> Unit,
    customKind: CustomKindInput,
    onCustomKindChange: (String) -> Unit,
    onAddCustomKind: () -> Unit,
    permissions: List<Permission>,
    isSelected: (Permission) -> Boolean,
    onSelectChange: (Permission, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(Res.string.add_permission_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        PermissionSearchField(
            search = search,
            onSearchChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        CustomKindRow(
            customKind = customKind,
            onCustomKindChange = onCustomKindChange,
            onAdd = onAddCustomKind,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(8.dp))

        PermissionChoices(
            permissions = permissions,
            isSelected = isSelected,
            onSelectChange = onSelectChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddPermissionsBottomBar(
    addEnabled: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Column {
            AmberButton(
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = addEnabled,
                onClick = onAdd,
                text = stringResource(Res.string.add),
            )
            IconRow(
                center = true,
                title = stringResource(Res.string.go_back),
                icon = ImageVector.vectorResource(R.drawable.back),
                onClick = onDismiss,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionSearchField(
    search: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = modifier,
        placeholder = { Text(stringResource(Res.string.search_permissions)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        singleLine = true,
    )
}

@Composable
private fun CustomKindRow(
    customKind: CustomKindInput,
    onCustomKindChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = customKind.text,
            onValueChange = onCustomKindChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(Res.string.custom_event_kind)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = customKind.alreadyAdded,
            supportingText = if (customKind.alreadyAdded) {
                { Text(stringResource(Res.string.custom_event_kind_already_added)) }
            } else {
                null
            },
        )
        TextButton(
            enabled = customKind.addEnabled,
            onClick = onAdd,
        ) {
            Text(stringResource(Res.string.add))
        }
    }
}

@Composable
private fun PermissionChoices(
    permissions: List<Permission>,
    isSelected: (Permission) -> Boolean,
    onSelectChange: (Permission, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (permissions.isEmpty()) {
        Text(
            modifier = modifier
                .padding(16.dp),
            text = stringResource(Res.string.no_permissions_found),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(
                permissions,
                key = { permissionKey(it) },
            ) { item ->
                PermissionChoiceRow(
                    permission = item,
                    selected = isSelected(item),
                    onToggle = { onSelectChange(item, !isSelected(item)) },
                    onSelectChange = { onSelectChange(item, it) },
                )
            }
        }
    }
}

@Composable
private fun PermissionChoiceRow(
    permission: Permission,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectChange,
        )
        Text(
            text = permission.localizedBlocking(true),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@PreviewsLightDark
@Composable
private fun AddPermissionsSheetPreview() {
    AmberPreview {
        AddPermissionsSheet(
            existingPermissions = listOf(
                Permission("get_public_key", null),
                Permission("sign_event", 1),
            ),
            onAdd = {},
            onDismiss = {},
        )
    }
}
