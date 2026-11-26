package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.add_permission
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.connect
import com.greenart7c3.nostrsigner.commonsui.go_back
import com.greenart7c3.nostrsigner.commonsui.permissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWithPubKey(
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    account: Account,
    packageName: String?,
    permissions: ImmutableList<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType, Account) -> Unit,
    onReject: (RememberType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPermissions = rememberLocalPermissions(permissions)

    val rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val accounts = rememberLoginAccounts(account)
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

    Column(modifier) {
        LoginContent(
            packageName = packageName,
            accounts = accounts,
            selectedAccountIndex = selectedAccountIndex,
            onSelectAccount = { selectedAccountIndex = it },
            scaffoldPadding = scaffoldPadding,
            horizontalPadding = horizontalPadding,
            modifier = Modifier.weight(1f),
        ) {
            ChooseSignPolicy(
                selectedOption = selectedOption,
                onSelect = { selectedOption = it },
            )

            if (selectedOption == 1) {
                ManualPermissions(
                    localPermissions = localPermissions,
                    sheetState = sheetState,
                    showModal = showModal,
                    showAddPermissions = showAddPermissions,
                    onShowModal = { showModal = it },
                    onShowAddPermissions = { showAddPermissions = it },
                    onAddPermissions = { addedPermissions -> localPermissions.addAll(addedPermissions) },
                )
            }
        }

        LoginButtons(
            horizontalPadding = horizontalPadding,
            onCancel = { onReject(rememberType) },
            onConnect = { onAccept(localPermissions, selectedOption, true, rememberType, accounts[selectedAccountIndex]) },
        )
    }
}

@Composable
private fun rememberLocalPermissions(permissions: ImmutableList<Permission>?): SnapshotStateList<Permission> = remember {
    val snapshot = mutableStateListOf<Permission>()
    permissions?.forEach {
        snapshot.add(it)
    }
    snapshot
}

@Composable
private fun rememberLoginAccounts(account: Account): SnapshotStateList<Account> = remember {
    val snapshot = mutableStateListOf<Account>()
    AccountStorage.cached().forEach {
        snapshot.add(it)
    }
    if (snapshot.none { it.hexKey == account.hexKey }) {
        snapshot.add(0, account)
    }
    snapshot
}

@Composable
private fun LoginContent(
    packageName: String?,
    accounts: List<Account>,
    selectedAccountIndex: Int,
    onSelectAccount: (Int) -> Unit,
    scaffoldPadding: PaddingValues,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    permissionsSection: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding),
    ) {
        packageName?.let {
            RequestingApp(packageName)
        }

        SectionLabel(stringResource(Res.string.account))
        AccountPickerRow(
            accounts = accounts,
            selectedAccountIndex = selectedAccountIndex,
            onSelect = onSelectAccount,
        )

        SectionLabel(stringResource(Res.string.permissions))
        permissionsSection()
    }
}

@Composable
private fun RequestingApp(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val appDisplayInfo = rememberAppDisplayInfo(packageName)
    Row(
        modifier = modifier.padding(vertical = 8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ManualPermissions(
    localPermissions: List<Permission>,
    sheetState: SheetState,
    showModal: Boolean,
    showAddPermissions: Boolean,
    onShowModal: (Boolean) -> Unit,
    onShowAddPermissions: (Boolean) -> Unit,
    onAddPermissions: (List<Permission>) -> Unit,
) {
    if (localPermissions.isNotEmpty()) {
        PermissionsButton(
            text = stringResource(Res.string.permissions),
            onClick = { onShowModal(true) },
        )
        if (showModal) {
            EnabledPermissionsSheet(
                localPermissions = localPermissions,
                sheetState = sheetState,
                onDismiss = { onShowModal(false) },
            )
        }
    }

    PermissionsButton(
        text = stringResource(Res.string.add_permission),
        onClick = { onShowAddPermissions(true) },
    )
    if (showAddPermissions) {
        AddPermissionsSheet(
            existingPermissions = localPermissions,
            onAdd = onAddPermissions,
            onDismiss = { onShowAddPermissions(false) },
        )
    }
}

@Composable
private fun PermissionsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
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
                Text(text)
            },
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnabledPermissionsSheet(
    localPermissions: List<Permission>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        modifier = modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Scaffold(
            bottomBar = {
                BottomAppBar {
                    IconRow(
                        center = true,
                        title = stringResource(Res.string.go_back),
                        icon = ImageVector.vectorResource(R.drawable.back),
                        onClick = onDismiss,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            EnabledPermissions(
                modifier = Modifier.padding(it),
                localPermissions = localPermissions,
            )
        }
    }
}

@Composable
private fun LoginButtons(
    horizontalPadding: Dp,
    onCancel: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(vertical = 8.dp),
        Arrangement.spacedBy(8.dp),
        Alignment.CenterVertically,
    ) {
        AmberButton(
            modifier = Modifier.weight(1f),
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6B00),
            ),
            text = stringResource(Res.string.cancel),
        )

        AmberButton(
            modifier = Modifier.weight(1f),
            onClick = onConnect,
            text = stringResource(Res.string.connect),
        )
    }
}

@PreviewsLightDark
@Composable
private fun LoginWithPubKeyPreview() {
    AmberPreview {
        LoginWithPubKey(
            horizontalPadding = 16.dp,
            scaffoldPadding = PaddingValues(),
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
