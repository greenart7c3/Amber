package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.add_permission
import com.greenart7c3.nostrsigner.commonsui.advanced
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.close_application
import com.greenart7c3.nostrsigner.commonsui.connect
import com.greenart7c3.nostrsigner.commonsui.go_back
import com.greenart7c3.nostrsigner.commonsui.permissions
import com.greenart7c3.nostrsigner.commonsui.relays_used
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.DeleteAfterSetting
import com.greenart7c3.nostrsigner.ui.DeleteAfterType
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.deleteAfterToSeconds
import com.greenart7c3.nostrsigner.ui.parseDeleteAfterType
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun BunkerConnectRequestScreen(
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    shouldCloseApp: Boolean,
    account: Account,
    bunkerRequest: AmberBunkerRequest,
    permissions: ImmutableList<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType, Long, Account) -> Unit,
    onReject: (RememberType) -> Unit,
    // The saved name of the connection a bunker:// secret belongs to, in the chosen account.
    modifier: Modifier = Modifier,
    loadConnectionName: suspend (npub: String, secret: String) -> String? = { _, _ -> null },
) {
    val state = remember { ConnectRequestState(bunkerRequest, account, permissions, shouldCloseApp) }
    val loadName by rememberUpdatedState(loadConnectionName)

    LaunchedEffect(state.selectedAccountIndex) {
        val secret = (bunkerRequest.request as BunkerRequestConnect).secret ?: ""
        state.appName = bunkerRequest.name.ifBlank {
            sanitizeAppName(loadName(state.selectedAccount.npub, secret) ?: "").ifBlank { bunkerRequest.localKey.toShortenHex() }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        bottomBar = {
            ConnectButtons(
                horizontalPadding = horizontalPadding,
                onCancel = { onReject(RememberType.NEVER) },
                onConnect = {
                    val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(state.deleteAfterIndex))
                    onAccept(state.localPermissions, state.selectedOption, state.closeApp, RememberType.ALWAYS, deleteAfter, state.selectedAccount)
                },
            )
        },
    ) { innerPadding ->
        ConnectRequestContent(
            state = state,
            bunkerRequest = bunkerRequest,
            contentPadding = innerPadding,
            horizontalPadding = horizontalPadding,
        )
    }
}

/** What the connect screen lets the user choose before connecting. */
@Stable
private class ConnectRequestState(
    bunkerRequest: AmberBunkerRequest,
    account: Account,
    permissions: ImmutableList<Permission>?,
    shouldCloseApp: Boolean,
) {
    val localPermissions = mutableStateListOf<Permission>().apply { permissions?.let { addAll(it) } }
    var selectedOption by mutableIntStateOf(account.signPolicy)
    val accounts = connectAccounts(bunkerRequest, account)
    var selectedAccountIndex by mutableIntStateOf(accounts.indexOfFirst { it.hexKey == account.hexKey }.coerceAtLeast(0))
    val selectedAccount: Account get() = accounts[selectedAccountIndex]
    val connectionRelays = bunkerRequest.relays
    var appName by mutableStateOf(bunkerRequest.name)
    var deleteAfterIndex by mutableIntStateOf(DeleteAfterType.NEVER.screenCode)
    var closeApp by mutableStateOf(shouldCloseApp)
}

/** The connecting app, then the account, relays, permissions and advanced options to connect with. */
@Composable
private fun ConnectRequestContent(
    state: ConnectRequestState,
    bunkerRequest: AmberBunkerRequest,
    contentPadding: PaddingValues,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding),
    ) {
        ConnectingApp(
            name = state.appName,
            imageUrl = bunkerRequest.clientMetadata?.image.orEmpty(),
            url = bunkerRequest.clientMetadata?.url.orEmpty(),
        )

        SectionLabel(stringResource(Res.string.account))
        AccountPickerRow(
            accounts = state.accounts,
            selectedAccountIndex = state.selectedAccountIndex,
            onSelect = { state.selectedAccountIndex = it },
        )

        if (state.connectionRelays.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.relays_used))
            ConnectionRelays(state.connectionRelays)
        }

        SectionLabel(stringResource(Res.string.permissions))
        ChooseSignPolicy(
            selectedOption = state.selectedOption,
            onSelect = {
                state.selectedOption = it
            },
        )

        if (state.selectedOption == 1) {
            ConnectPermissions(state.localPermissions)
        }

        AdvancedConnectOptions(
            closeApp = state.closeApp,
            onCloseAppChange = { state.closeApp = it },
            deleteAfterIndex = state.deleteAfterIndex,
            onDeleteAfterChange = { state.deleteAfterIndex = it },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * The accounts the connection can be made with: every account for a nostrconnect:// URI (the
 * request's own first, when it is not already among them), else only the request's account.
 */
private fun connectAccounts(
    bunkerRequest: AmberBunkerRequest,
    account: Account,
): SnapshotStateList<Account> {
    val snapshot = mutableStateListOf<Account>()
    if (bunkerRequest.isNostrConnectUri) {
        AccountStorage.cached().forEach {
            snapshot.add(it)
        }
        if (snapshot.none { it.hexKey == account.hexKey }) {
            snapshot.add(0, account)
        }
    } else {
        snapshot.add(account)
    }
    return snapshot
}

@Composable
private fun ConnectButtons(
    horizontalPadding: Dp,
    onCancel: () -> Unit,
    onConnect: () -> Unit,
) {
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

/** The connecting app's icon, name and, when it gave one, its URL. */
@Composable
private fun ConnectingApp(
    name: String,
    imageUrl: String,
    url: String,
) {
    Column {
        RemoteAppIcon(imageUrl = imageUrl, name = name, size = 64.dp)

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            text = name,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )

        if (url.isNotBlank()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                text = url,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The relays the connection uses, each with its trust score when scoring is on. */
@Composable
private fun ConnectionRelays(relays: List<NormalizedRelayUrl>) {
    val settingsStore = LocalSettingsStore.current
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(relays) {
        if (TrustScoreService.isEnabled(settingsStore.settings) && relays.isNotEmpty()) {
            relays.forEach { relay ->
                val url = relay.url
                if (!trustScores.containsKey(url)) {
                    loadingScores[url] = true
                    launch {
                        val score = withContext(Dispatchers.IO) { TrustScoreService.getScore(url, settingsStore.settings) }
                        trustScores[url] = score
                        loadingScores[url] = false
                    }
                }
            }
        }
    }

    Column {
        relays.forEach { relay ->
            ConnectionRelayRow(
                url = relay.url,
                score = trustScores[relay.url],
                isLoadingScore = loadingScores[relay.url] == true,
            )
        }
    }
}

@Composable
private fun ConnectionRelayRow(
    url: String,
    score: Int?,
    isLoadingScore: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = url,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (!TrustScoreService.isExcludedFromScoring(url)) {
            TrustScoreBadge(
                score = score,
                isLoading = isLoadingScore,
            )
        }
    }
}

/** With manual approval: the requested permissions (in a sheet) and a button to add more. */
@Composable
private fun ConnectPermissions(localPermissions: SnapshotStateList<Permission>) {
    var showModal by remember { mutableStateOf(false) }
    var showAddPermissions by remember { mutableStateOf(false) }

    Column {
        if (localPermissions.isNotEmpty()) {
            CenteredButton(text = stringResource(Res.string.permissions), onClick = { showModal = true })
            if (showModal) {
                RequestedPermissionsSheet(localPermissions = localPermissions, onDismiss = { showModal = false })
            }
        }

        CenteredButton(text = stringResource(Res.string.add_permission), onClick = { showAddPermissions = true })
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

@Composable
private fun CenteredButton(
    text: String,
    onClick: () -> Unit,
) {
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
                Text(text)
            },
            onClick = onClick,
        )
    }
}

/** The permissions the app asks for, each of which can be turned off before connecting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestedPermissionsSheet(
    localPermissions: SnapshotStateList<Permission>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
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

/** The collapsed Advanced section: close the app after answering, and when the connection deletes itself. */
@Composable
private fun AdvancedConnectOptions(
    closeApp: Boolean,
    onCloseAppChange: (Boolean) -> Unit,
    deleteAfterIndex: Int,
    onDeleteAfterChange: (Int) -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advancedOpen = !advancedOpen }
                .padding(top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.advanced).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (advancedOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = advancedOpen) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCloseAppChange(!closeApp) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.close_application),
                        fontSize = 14.sp,
                    )
                    Switch(
                        checked = closeApp,
                        onCheckedChange = onCloseAppChange,
                    )
                }

                DeleteAfterSetting(
                    selectedIndex = deleteAfterIndex,
                    onSelect = onDeleteAfterChange,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
