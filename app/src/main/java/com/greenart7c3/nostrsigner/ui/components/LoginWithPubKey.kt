package com.greenart7c3.nostrsigner.ui.components

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
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.actions.AdjustPermissionsDialog
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun LoginWithPubKey(
    appName: String,
    applicationName: String?,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?) -> Unit,
    onReject: () -> Unit,
) {
    var localPermissions by remember {
        mutableStateOf(permissions)
    }

    var showAdjustDialog by remember {
        mutableStateOf(false)
    }

    if (showAdjustDialog) {
        AdjustPermissionsDialog(
            localPermissions ?: emptyList(),
            onClose = {
                showAdjustDialog = false
            },
        ) { dialogPermissions ->
            localPermissions =
                dialogPermissions.map {
                    it.copy()
                }
            showAdjustDialog = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf))
            },
            fontSize = 18.sp,
        )

        Spacer(Modifier.size(4.dp))

        localPermissions?.let {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(it.take(3)) { _, item ->
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Icon(
                            if (item.checked) Icons.Default.Done else Icons.Default.Close,
                            item.toString(),
                            tint = if (item.checked) Color.Green else Color.Red,
                        )
                        Text(text = item.toString())
                    }
                }
            }
            if (it.size > 3) {
                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    val isAnyChecked = it.take(3).any { permission -> permission.checked }
                    Icon(
                        if (isAnyChecked) Icons.Default.Done else Icons.Default.Close,
                        stringResource(R.string.and_more),
                        tint = if (isAnyChecked) Color.Green else Color.Red,
                    )
                    Text(text = stringResource(R.string.more, it.size - 3))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally,
        ) {
            localPermissions?.let {
                if (it.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            showAdjustDialog = true
                        },
                        modifier = Modifier
                            .padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.adjust))
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
                },
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier =
                Modifier
                    .padding(8.dp),
                shape = ButtonBorder,
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5A5554),
                ),
            ) {
                Text(stringResource(R.string.reject))
            }
        }
    }
}
