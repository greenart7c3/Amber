package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWithPubKey(
    horizontalPadding: Dp,
    modifier: Modifier,
    account: Account,
    packageName: String?,
    permissions: ImmutableList<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType, Account) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }

    val rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val accounts = remember {
        val snapshot = mutableStateListOf<Account>()
        LocalPreferences.allCachedAccounts().forEach {
            snapshot.add(it)
        }
        if (snapshot.none { it.hexKey == account.hexKey }) {
            snapshot.add(0, account)
        }
        snapshot
    }
    var selectedAccountIndex by remember {
        mutableIntStateOf(
            accounts.indexOfFirst { it.hexKey == account.hexKey }.coerceAtLeast(0),
        )
    }
    var showModal by remember { mutableStateOf(false) }
    var showAddPermissions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    Column {
        Column(
            modifier = modifier
                .weight(1f),
        ) {
            packageName?.let {
                val appDisplayInfo = rememberAppDisplayInfo(packageName)
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (appDisplayInfo.icon != null) {
                        Image(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.small),
                            bitmap = appDisplayInfo.icon.toBitmap().asImageBitmap(),
                            contentDescription = appDisplayInfo.name,
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Column {
                        Text(
                            text = appDisplayInfo.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = packageName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Account section
            SectionLabel(stringResource(R.string.account))
            AccountPickerRow(
                accounts = accounts,
                selectedAccountIndex = selectedAccountIndex,
                onSelect = { selectedAccountIndex = it },
            )

            // Permissions section
            SectionLabel(stringResource(R.string.permissions))
            ChooseSignPolicy(
                selectedOption = selectedOption,
                onSelected = {
                    selectedOption = it
                },
            )

            if (selectedOption == 1) {
                if (localPermissions.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ElevatedButton(
                            colors = ButtonDefaults.buttonColors().copy(
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(20),
                            content = {
                                Text(stringResource(R.string.permissions))
                            },
                            onClick = {
                                showModal = true
                            },
                        )
                    }
                    if (showModal) {
                        ModalBottomSheet(
                            sheetState = sheetState,
                            onDismissRequest = {
                                showModal = false
                            },
                        ) {
                            Scaffold(
                                bottomBar = {
                                    BottomAppBar {
                                        IconRow(
                                            center = true,
                                            title = stringResource(R.string.go_back),
                                            icon = ImageVector.vectorResource(R.drawable.back),
                                            onClick = {
                                                showModal = false
                                            },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            ) {
                                EnabledPermissions(
                                    Modifier.padding(it),
                                    localPermissions,
                                )
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ElevatedButton(
                        colors = ButtonDefaults.buttonColors().copy(
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(20),
                        content = {
                            Text(stringResource(R.string.add_permission))
                        },
                        onClick = {
                            showAddPermissions = true
                        },
                    )
                }
                if (showAddPermissions) {
                    AddPermissionsSheet(
                        existingPermissions = localPermissions,
                        onAdd = { addedPermissions ->
                            localPermissions.addAll(addedPermissions)
                        },
                        onDismiss = {
                            showAddPermissions = false
                        },
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(vertical = 8.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterVertically,
        ) {
            AmberButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    onReject(rememberType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                ),
                text = stringResource(R.string.cancel),
            )

            AmberButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    onAccept(localPermissions, selectedOption, true, rememberType, accounts[selectedAccountIndex])
                },
                text = stringResource(R.string.connect),
            )
        }
    }
}

@ThemePreviews
@Composable
fun LoginWithPubKeyPreview() {
    AmberPreview {
        LoginWithPubKey(
            horizontalPadding = 16.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
            account = previewAccount(),
            packageName = null,
            permissions = persistentListOf(
                Permission("get_public_key", null),
                Permission("sign_event", 1),
                Permission("nip04_encrypt", null),
            ),
            onAccept = { _, _, _, _, _ -> },
            onReject = {},
        )
    }
}
