package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ProfilePictureIcon(account: Account) {
    val profileUrl by account.picture.collectAsStateWithLifecycle()
    if (profileUrl.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
        AsyncImage(
            profileUrl,
            Route.Accounts.route,
            Modifier
                .clip(
                    RoundedCornerShape(50),
                )
                .height(40.dp)
                .width(40.dp),
        )
    } else {
        Icon(
            Icons.Outlined.Person,
            Route.Accounts.route,
            modifier = Modifier
                .border(
                    2.dp,
                    Color.fromHex(account.hexKey.slice(0..5)),
                    CircleShape,
                )
                .height(40.dp)
                .width(40.dp),
        )
    }
}

/** Small uppercase muted section heading — matches the screenshot mock style. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Single tappable account row with avatar + name + Switch pill.
 * When [accounts] has more than one entry, tapping the row opens a bottom-sheet
 * picker. With a single account the row is inert and the Switch pill is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerRow(
    accounts: List<Account>,
    selectedAccountIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSwitch = accounts.size > 1
    val selected = accounts.getOrNull(selectedAccountIndex) ?: accounts.first()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = canSwitch) { sheetOpen = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePictureIcon(account = selected)
        Spacer(Modifier.width(12.dp))
        val name by selected.name.collectAsStateWithLifecycle()
        Text(
            text = name.ifBlank { selected.npub.toShortenHex() },
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            maxLines = 1,
        )
        if (canSwitch) {
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.switch_account),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { sheetOpen = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.select_account),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                accounts.forEachIndexed { index, acc ->
                    val name by acc.name.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(index)
                                sheetOpen = false
                            }
                            .background(
                                color = if (index == selectedAccountIndex) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfilePictureIcon(account = acc)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank { acc.npub.toShortenHex() },
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                            if (name.isNotBlank()) {
                                Text(
                                    text = acc.npub.toShortenHex(),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index == selectedAccountIndex) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF1D8802),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

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

            if (selectedOption == 1 && localPermissions.isNotEmpty()) {
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
