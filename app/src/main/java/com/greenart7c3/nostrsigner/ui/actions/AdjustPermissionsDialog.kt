package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.PostButton

@Composable
fun AdjustPermissionsDialog(
    localPermissions: List<Permission>,
    onClose: () -> Unit,
    onPost: (List<Permission>) -> Unit,
) {
    var dialogPermissions by remember {
        mutableStateOf(localPermissions.map { it.copy() })
    }
    var selectAll by remember {
        mutableStateOf(true)
    }
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onCancel = onClose,
                    )

                    PostButton(
                        isActive = true,
                        onPost = {
                            onPost(dialogPermissions)
                        },
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable {
                            selectAll = !selectAll
                            dialogPermissions =
                                dialogPermissions.map { item ->
                                    item.copy(checked = selectAll)
                                }
                        },
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.select_deselect_all),
                    )
                    Switch(
                        checked = selectAll,
                        onCheckedChange = {
                            selectAll = !selectAll
                            dialogPermissions =
                                dialogPermissions.map { item ->
                                    item.copy(checked = selectAll)
                                }
                        },
                    )
                }

                LazyColumn(
                    Modifier.padding(16.dp),
                ) {
                    items(dialogPermissions.size) {
                        val permission = dialogPermissions[it]
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    dialogPermissions =
                                        dialogPermissions.mapIndexed { j, item ->
                                            if (it == j) {
                                                item.copy(checked = !item.checked)
                                            } else {
                                                item
                                            }
                                        }
                                },
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = permission.toLocalizedString(context),
                            )
                            Switch(
                                checked = permission.checked,
                                onCheckedChange = { _ ->
                                    dialogPermissions =
                                        dialogPermissions.mapIndexed { j, item ->
                                            if (it == j) {
                                                item.copy(checked = !item.checked)
                                            } else {
                                                item
                                            }
                                        }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
