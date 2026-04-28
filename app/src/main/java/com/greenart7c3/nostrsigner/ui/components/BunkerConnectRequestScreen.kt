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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.DeleteAfterType
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.SettingsRow
import com.greenart7c3.nostrsigner.ui.deleteAfterToSeconds
import com.greenart7c3.nostrsigner.ui.parseDeleteAfterType
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val scope = rememberCoroutineScope()
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val accounts = remember {
        val snapshot = mutableStateListOf<Account>()
        if (bunkerRequest.isNostrConnectUri) {
            LocalPreferences.allCachedAccounts().forEach {
                snapshot.add(it)
            }
            if (snapshot.none { it.hexKey == account.hexKey }) {
                snapshot.add(0, account)
            }
        } else {
            snapshot.add(account)
        }
        snapshot
    }
    var selectedAccountIndex by remember {
        mutableIntStateOf(
            accounts.indexOfFirst { it.hexKey == account.hexKey }.coerceAtLeast(0),
        )
    }

    // Trust scores for connection request relays
    val connectionRelays = remember { bunkerRequest.relays }
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }
    val appName = remember { mutableStateOf(bunkerRequest.name) }

    LaunchedEffect(selectedAccountIndex) {
        scope.launch(Dispatchers.IO) {
            val account = accounts[selectedAccountIndex]
            appName.value = bunkerRequest.name.ifBlank { Amber.instance.getDatabase(account.npub).dao().getBySecret((bunkerRequest.request as BunkerRequestConnect).secret ?: "")?.application?.name ?: bunkerRequest.localKey.toShortenHex() }
        }
    }

    // Fetch trust scores for connection relays
    LaunchedEffect(connectionRelays) {
        if (!BuildFlavorChecker.isOfflineFlavor() && connectionRelays.isNotEmpty()) {
            connectionRelays.forEach { relay ->
                val url = relay.url
                if (!trustScores.containsKey(url)) {
                    loadingScores[url] = true
                    scope.launch(Dispatchers.IO) {
                        val score = TrustScoreService.getScore(url)
                        trustScores[url] = score
                        loadingScores[url] = false
                    }
                }
            }
        }
    }
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
    var showModal by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        bottomBar = {
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
                        onReject(RememberType.NEVER)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B00),
                    ),
                    text = stringResource(R.string.cancel),
                )

                AmberButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(deleteAfterIndex))
                        onAccept(localPermissions, selectedOption, closeApp, RememberType.ALWAYS, deleteAfter, accounts[selectedAccountIndex])
                    },
                    text = stringResource(R.string.connect),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                text = appName.value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )

            // Account section
            SectionLabel(stringResource(R.string.account))
            AccountPickerRow(
                accounts = accounts,
                selectedAccountIndex = selectedAccountIndex,
                onSelect = { selectedAccountIndex = it },
            )

            // Relays with trust scores
            if (connectionRelays.isNotEmpty()) {
                SectionLabel(stringResource(R.string.relays_used))
                connectionRelays.forEach { relay ->
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
                            text = relay.url,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        TrustScoreBadge(
                            score = trustScores[relay.url],
                            isLoading = loadingScores[relay.url] == true,
                        )
                    }
                }
            }

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

            // Advanced — collapsible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.advanced).uppercase(),
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
                    // Close app toggle
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                closeApp = !closeApp
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.close_application),
                            fontSize = 14.sp,
                        )
                        Switch(
                            checked = closeApp,
                            onCheckedChange = {
                                closeApp = it
                            },
                        )
                    }

                    Box(
                        Modifier.padding(vertical = 6.dp),
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
