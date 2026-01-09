package com.greenart7c3.nostrsigner.ui.components

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.DeleteAfterType
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.SettingsRow
import com.greenart7c3.nostrsigner.ui.deleteAfterToSeconds
import com.greenart7c3.nostrsigner.ui.parseDeleteAfterType
import kotlin.collections.forEach
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkerConnectRequestScreen(
    modifier: Modifier,
    shouldCloseApp: Boolean,
    account: Account,
    bunkerRequest: AmberBunkerRequest,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType, Long, Account) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val accounts = remember {
        val snapshot = mutableStateListOf<Account>()
        if (bunkerRequest.isNostrConnectUri) {
            LocalPreferences.allAccounts(Amber.instance).forEach {
                snapshot.add(it)
            }
        } else {
            snapshot.add(account)
        }
        snapshot
    }
    var selectedAccountIndex by remember { mutableIntStateOf(accounts.indexOf(account)) }
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    val deleteAfterItems =
        persistentListOf(
            TitleExplainer(stringResource(DeleteAfterType.NEVER.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.TEN_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_HOUR.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_DAY.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_WEEK.resourceId)),
        )
    var deleteAfterIndex by remember { mutableIntStateOf(DeleteAfterType.NEVER.screenCode) }
    var closeApp by remember { mutableStateOf(shouldCloseApp) }

    Column(
        modifier,
    ) {
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

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable {
                        closeApp = !closeApp
                    },
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.close_application),
                )
                Switch(
                    checked = closeApp,
                    onCheckedChange = {
                        closeApp = it
                    },
                )
            }

            Box(
                Modifier.padding(4.dp),
            ) {
                SettingsRow(
                    R.string.delete_after,
                    null,
                    deleteAfterItems,
                    deleteAfterIndex,
                ) {
                    deleteAfterIndex = it
                }
            }

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
                    val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(deleteAfterIndex))
                    onAccept(localPermissions, selectedOption, closeApp, rememberType, deleteAfter, accounts[selectedAccountIndex])
                },
                text = stringResource(R.string.connect),
            )
        }
    }
}
