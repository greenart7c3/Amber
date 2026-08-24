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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.supportedKindNumbers
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews

private fun permissionKey(permission: Permission) = "${permission.type}-${permission.kind}"

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
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val candidates = remember(existingPermissions) {
        supportedKindNumbers
            .distinctBy { permissionKey(it) }
            .filterNot { candidate ->
                existingPermissions.any {
                    it.type.equals(candidate.type, ignoreCase = true) && it.kind == candidate.kind
                }
            }
    }

    val selectedPermissions = remember { mutableStateMapOf<String, Boolean>() }
    var search by remember { mutableStateOf("") }

    // Custom sign_event kinds typed by the user, added on top of the catalog list.
    val customPermissions = remember { mutableStateListOf<Permission>() }
    var customKind by remember { mutableStateOf("") }
    val parsedCustomKind = customKind.toIntOrNull()
    val customKindExists = parsedCustomKind != null &&
        existingPermissions.any {
            it.type.equals("sign_event", ignoreCase = true) && it.kind == parsedCustomKind
        }
    val canAddCustomKind = parsedCustomKind != null &&
        !customKindExists &&
        customPermissions.none { it.kind == parsedCustomKind }

    val customCandidates = customPermissions.toList()
    val allCandidates = customCandidates + candidates
    val filteredPermissions = remember(allCandidates, search) {
        if (search.isBlank()) {
            allCandidates
        } else {
            allCandidates.filter {
                it.toLocalizedString(context, true).contains(search, ignoreCase = true) ||
                    it.kind.toString().contains(search) ||
                    it.type.contains(search, ignoreCase = true)
            }
        }
    }

    val hasSelection = selectedPermissions.values.any { it }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Scaffold(
            bottomBar = {
                BottomAppBar {
                    Column {
                        AmberButton(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            enabled = hasSelection,
                            onClick = {
                                onAdd(
                                    allCandidates.filter { selectedPermissions[permissionKey(it)] == true },
                                )
                                onDismiss()
                            },
                            text = stringResource(R.string.add),
                        )
                        IconRow(
                            center = true,
                            title = stringResource(R.string.go_back),
                            icon = ImageVector.vectorResource(R.drawable.back),
                            onClick = onDismiss,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxWidth(),
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.add_permission_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_permissions)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = customKind,
                        onValueChange = { customKind = it.filter { char -> char.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.custom_event_kind)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = customKindExists,
                        supportingText = if (customKindExists) {
                            { Text(stringResource(R.string.custom_event_kind_already_added)) }
                        } else {
                            null
                        },
                    )
                    TextButton(
                        enabled = canAddCustomKind,
                        onClick = {
                            val kind = parsedCustomKind ?: return@TextButton
                            val permission = Permission("sign_event", kind)
                            customPermissions.add(permission)
                            selectedPermissions[permissionKey(permission)] = true
                            customKind = ""
                        },
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (filteredPermissions.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        text = stringResource(R.string.no_permissions_found),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        items(
                            filteredPermissions,
                            key = { permissionKey(it) },
                        ) { item ->
                            val itemKey = permissionKey(item)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPermissions[itemKey] = !(selectedPermissions[itemKey] ?: false)
                                    }
                                    .padding(vertical = 2.dp),
                            ) {
                                Checkbox(
                                    checked = selectedPermissions[itemKey] ?: false,
                                    onCheckedChange = {
                                        selectedPermissions[itemKey] = it
                                    },
                                )
                                Text(
                                    text = item.toLocalizedString(context, true),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
fun AddPermissionsSheetPreview() {
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
