package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWithPubKey(
    modifier: Modifier,
    account: Account,
    packageName: String?,
    permissions: List<Permission>?,
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

    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val accounts = remember {
        val snapshot = mutableStateListOf<Account>()
        LocalPreferences.allCachedAccounts().forEach {
            snapshot.add(it)
        }
        snapshot
    }
    var selectedAccountIndex by remember { mutableIntStateOf(accounts.indexOf(account)) }

    Column(
        modifier,
    ) {
        packageName?.let {
            val appDisplayInfo = rememberAppDisplayInfo(packageName)
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (appDisplayInfo.icon != null) {
                    Image(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small),
                        bitmap = appDisplayInfo.icon.toBitmap().asImageBitmap(),
                        contentDescription = appDisplayInfo.name,
                        contentScale = ContentScale.Crop,
                    )
                }

                Text(
                    text = appDisplayInfo.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = packageName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = {
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(selectedTabIndex)
                        .height(6.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 2.dp)
                        .clip(CircleShape)
                        .background(color = MaterialTheme.colorScheme.primary),
                )
            },
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            },
        ) {
            SignerConnectAppTab(
                text = stringResource(id = R.string.login).uppercase(),
                selected = selectedTabIndex == 0,
                onClick = {
                    selectedTabIndex = 0
                },
            )

            SignerConnectAppTab(
                text = stringResource(id = R.string.permissions).uppercase(),
                selected = selectedTabIndex == 0,
                onClick = {
                    selectedTabIndex = 1
                },
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        if (selectedTabIndex == 0) {
            accounts.forEachIndexed { index, acc ->
                ListItem(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (selectedAccountIndex == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(4.dp)
                        .selectable(
                            selected = selectedAccountIndex == index,
                            onClick = {
                                selectedAccountIndex = index
                            },
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,

                    ),
                    leadingContent = {
                        ProfilePictureIcon(
                            account = acc,
                        )
                    },
                    headlineContent = {
                        val name by acc.name.collectAsStateWithLifecycle()
                        Text(
                            name.ifBlank { acc.npub.toShortenHex() },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        } else {
            var showModal by remember { mutableStateOf(false) }
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )

            ChooseSignPolicy(
                selectedOption = selectedOption,
                onSelected = {
                    selectedOption = it
                },
            )

            if (selectedOption == 1 && localPermissions.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterVertically,
        ) {
            AmberButton(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .weight(1f),
                onClick = {
                    onReject(rememberType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                ),
                text = stringResource(R.string.cancel),
            )

            AmberButton(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .weight(1f),
                onClick = {
                    onAccept(localPermissions, selectedOption, true, rememberType, accounts[selectedAccountIndex])
                },
                text = stringResource(R.string.connect),
            )
        }
    }
}
