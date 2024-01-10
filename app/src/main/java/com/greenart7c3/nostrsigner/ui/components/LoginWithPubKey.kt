package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun LoginWithPubKey(
    appName: String,
    applicationName: String?,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?) -> Unit,
    onReject: () -> Unit
) {
    var localPermissions by remember {
        mutableStateOf(permissions)
    }

    var showAdjustDialog by remember {
        mutableStateOf(false)
    }

    if (showAdjustDialog) {
        var dialogPermissions by remember {
            mutableStateOf(localPermissions?.map { it.copy() } ?: listOf())
        }
        var selectAll by remember {
            mutableStateOf(true)
        }
        Dialog(
            onDismissRequest = {
                showAdjustDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloseButton(
                            onCancel = {
                                showAdjustDialog = false
                            }
                        )

                        PostButton(
                            isActive = true,
                            onPost = {
                                localPermissions = dialogPermissions.map {
                                    it.copy()
                                }
                                showAdjustDialog = false
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable {
                                selectAll = !selectAll
                                dialogPermissions = dialogPermissions.mapIndexed { j, item ->
                                    item.copy(checked = selectAll)
                                }
                            }
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.select_deselect_all)
                        )
                        Switch(
                            checked = selectAll,
                            onCheckedChange = {
                                selectAll = !selectAll
                                dialogPermissions = dialogPermissions.mapIndexed { j, item ->
                                    item.copy(checked = selectAll)
                                }
                            }
                        )
                    }

                    LazyColumn(
                        Modifier.padding(16.dp)
                    ) {
                        items(dialogPermissions.size) {
                            val permission = dialogPermissions[it]
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        dialogPermissions = dialogPermissions.mapIndexed { j, item ->
                                            if (it == j) {
                                                item.copy(checked = !item.checked)
                                            } else {
                                                item
                                            }
                                        }
                                    }
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = permission.toString()
                                )
                                Switch(
                                    checked = permission.checked,
                                    onCheckedChange = { _ ->
                                        dialogPermissions = dialogPermissions.mapIndexed { j, item ->
                                            if (it == j) {
                                                item.copy(checked = !item.checked)
                                            } else {
                                                item
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(" would like your permission to read your public key and sign events on your behalf")
            },
            fontSize = 18.sp
        )

        Spacer(Modifier.size(4.dp))

        localPermissions?.let {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(it.take(3)) { _, item ->
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            if (item.checked) Icons.Default.Done else Icons.Default.Close,
                            item.toString(),
                            tint = if (item.checked) Color.Green else Color.Red
                        )
                        Text(text = item.toString())
                    }
                }
            }
            if (it.size > 3) {
                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    val isAnyChecked = it.take(3).any { permission -> permission.checked }
                    Icon(
                        if (isAnyChecked) Icons.Default.Done else Icons.Default.Close,
                        "...and more",
                        tint = if (isAnyChecked) Color.Green else Color.Red
                    )
                    Text(text = "${it.size - 3} more...")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally
        ) {
            localPermissions?.let {
                if (it.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            showAdjustDialog = true
                        },
                        modifier = Modifier
                            .padding(8.dp)
                    ) {
                        Text(text = "Adjust")
                    }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(8.dp),
                shape = ButtonBorder,
                onClick = {
                    onAccept(localPermissions)
                }
            ) {
                Text("Grant Permissions")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier = Modifier
                    .padding(8.dp),
                shape = ButtonBorder,
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5A5554)
                )
            ) {
                Text(stringResource(R.string.reject))
            }
        }
    }
}
