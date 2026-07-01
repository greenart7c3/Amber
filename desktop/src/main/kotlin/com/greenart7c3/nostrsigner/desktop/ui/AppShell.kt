package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.AccountStore
import com.greenart7c3.nostrsigner.desktop.data.AppDataDir
import com.greenart7c3.nostrsigner.desktop.data.BunkerDatabase
import com.greenart7c3.nostrsigner.desktop.data.ConnectedApp
import com.greenart7c3.nostrsigner.desktop.data.RelayStore
import com.greenart7c3.nostrsigner.desktop.data.SettingsStore
import com.greenart7c3.nostrsigner.desktop.data.SqliteBunkerHistoryLogger
import com.greenart7c3.nostrsigner.desktop.data.SqliteBunkerPermissionStore
import com.greenart7c3.nostrsigner.desktop.data.StoredPermission
import com.greenart7c3.nostrsigner.desktop.relay.BunkerRelayConnection
import com.greenart7c3.nostrsigner.desktop.relay.DEFAULT_BUNKER_RELAYS
import com.greenart7c3.nostrsigner.desktop.ui.components.ConfirmDialog
import com.greenart7c3.nostrsigner.desktop.ui.components.copyToClipboard
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex
import com.greenart7c3.nostrsigner.desktop.ui.nav.Screen
import com.greenart7c3.nostrsigner.desktop.ui.theme.DesktopTheme
import com.greenart7c3.nostrsigner.desktop.ui.theme.ThemeMode
import com.greenart7c3.nostrsigner.desktop.ui.theme.resolveIsDark
import com.greenart7c3.nostrsigner.shared.BunkerHistoryEntry
import com.greenart7c3.nostrsigner.shared.BunkerSigner
import com.greenart7c3.nostrsigner.shared.BunkerSigningEngine
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.awt.Desktop
import java.sql.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class BunkerServices(
    val connection: Connection,
    val permissionStore: SqliteBunkerPermissionStore,
    val historyLogger: SqliteBunkerHistoryLogger,
    val relayStore: RelayStore,
    val settingsStore: SettingsStore,
    val approvalPort: DesktopApprovalPort,
    val engine: BunkerSigningEngine,
)

/** The desktop bunker's main shell: nav rail + current screen + the approval dialog overlay. */
@Composable
fun AppShell(
    pubKeyHex: String,
    accounts: List<String>,
    scope: CoroutineScope,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: (String) -> Unit,
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var account by remember(pubKeyHex) { mutableStateOf<KeyPair?>(null) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(pubKeyHex) {
        account = AccountStore.load(pubKeyHex)
    }

    val currentAccount = account
    if (currentAccount == null) {
        DesktopTheme(darkTheme = themeMode.resolveIsDark()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Text("Loading...") }
        }
        return
    }

    val services = remember(pubKeyHex, currentAccount) {
        val db = BunkerDatabase.open(pubKeyHex)
        val permissionStore = SqliteBunkerPermissionStore(db)
        val historyLogger = SqliteBunkerHistoryLogger(db)
        val approvalPort = DesktopApprovalPort()
        val engine = BunkerSigningEngine(
            account = BunkerSigner(currentAccount),
            permissionStore = permissionStore,
            approvalPort = approvalPort,
            historyLogger = historyLogger,
            appNameLookup = { pk -> historyLogger.nameFor(pk) },
        )
        BunkerServices(
            connection = db,
            permissionStore = permissionStore,
            historyLogger = historyLogger,
            relayStore = RelayStore(db),
            settingsStore = SettingsStore(db),
            approvalPort = approvalPort,
            engine = engine,
        )
    }

    // Approval dialogs / callbacks for one connection lifecycle.
    var relayConnection by remember(pubKeyHex) { mutableStateOf<BunkerRelayConnection?>(null) }
    var connectedRelayCount by remember { mutableStateOf(0) }

    LaunchedEffect(pubKeyHex) {
        services.settingsStore.get("theme_mode")?.let { saved ->
            themeMode = runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
        }

        val persistedRelays = services.relayStore.list()
        val relayUrls = persistedRelays.ifEmpty { DEFAULT_BUNKER_RELAYS }
        val connection = BunkerRelayConnection(pubKeyHex, services.engine, scope, relayUrls)
        connection.start()
        relayConnection = connection
    }

    DisposableEffect(pubKeyHex) {
        onDispose { relayConnection?.stop() }
    }

    LaunchedEffect(relayConnection) {
        relayConnection?.connectedRelays?.collect { connectedRelayCount = it.size }
    }

    val pending by services.approvalPort.pending.collectAsState()

    var connectedApps by remember { mutableStateOf<List<ConnectedApp>>(emptyList()) }
    suspend fun refreshConnectedApps() {
        connectedApps = services.historyLogger.connectedApps()
    }
    LaunchedEffect(Unit) { refreshConnectedApps() }
    LaunchedEffect(pending.size) { refreshConnectedApps() }

    var detailPermissions by remember { mutableStateOf<List<StoredPermission>>(emptyList()) }
    var detailActivity by remember { mutableStateOf<List<BunkerHistoryEntry>>(emptyList()) }
    suspend fun refreshDetail(appPubKey: String) {
        detailPermissions = services.permissionStore.permissionsFor(appPubKey)
        detailActivity = services.historyLogger.recentHistoryFor(appPubKey).map { it.entry }
    }
    LaunchedEffect(currentScreen) {
        (currentScreen as? Screen.AppDetail)?.let { refreshDetail(it.appPubKey) }
    }

    var relayList by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Settings) relayList = services.relayStore.list()
    }

    pending.firstOrNull()?.let { entry ->
        ApprovalDialog(entry, onAnswer = { approved, remember -> services.approvalPort.answer(entry, approved, remember) })
    }

    DesktopTheme(darkTheme = themeMode.resolveIsDark()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(currentScreen, pendingCount = pending.size, onNavigate = { currentScreen = it })

            Box(modifier = Modifier.weight(1f)) {
                when (val screen = currentScreen) {
                    Screen.Home -> HomeScreen(
                        pubKeyHex = pubKeyHex,
                        totalRelays = relayConnection?.relays?.size ?: 0,
                        connectedRelayCount = connectedRelayCount,
                        connectedAppsCount = connectedApps.size,
                        pendingApprovalCount = pending.size,
                        onCopyPubKey = { copyToClipboard(pubKeyHex) },
                        onOpenAccountMenu = { showAccountSwitcher = true },
                    )
                    Screen.Connect -> ConnectScreen(
                        bunkerUri = relayConnection?.let { conn -> "bunker://$pubKeyHex?" + conn.relays.joinToString("&") { "relay=${it.url}" } },
                        onCopyUri = { copyToClipboard(it) },
                    )
                    Screen.ConnectedApps -> ConnectedAppsScreen(
                        connectedApps = connectedApps,
                        onAppClick = { currentScreen = Screen.AppDetail(it) },
                    )
                    is Screen.AppDetail -> {
                        val app = connectedApps.find { it.pubKey == screen.appPubKey }
                        AppDetailScreen(
                            appPubKey = screen.appPubKey,
                            appName = app?.name.orEmpty(),
                            bunkerUri = "bunker://$pubKeyHex?" + (relayConnection?.relays?.joinToString("&") { "relay=${it.url}" } ?: ""),
                            permissions = detailPermissions,
                            recentActivity = detailActivity,
                            onAllow = { permission ->
                                scope.launch {
                                    services.permissionStore.remember(permission.appPubKey, permission.method, permission.kind, true)
                                    refreshDetail(screen.appPubKey)
                                }
                            },
                            onDeny = { permission ->
                                scope.launch {
                                    services.permissionStore.remember(permission.appPubKey, permission.method, permission.kind, false)
                                    refreshDetail(screen.appPubKey)
                                }
                            },
                            onAsk = { permission ->
                                scope.launch {
                                    services.permissionStore.deletePermission(permission.appPubKey, permission.method, permission.kind)
                                    refreshDetail(screen.appPubKey)
                                }
                            },
                            onRevokeAll = {
                                scope.launch {
                                    services.permissionStore.revokeAll(screen.appPubKey)
                                    refreshDetail(screen.appPubKey)
                                }
                            },
                            onRemoveApp = {
                                scope.launch {
                                    services.permissionStore.revokeAll(screen.appPubKey)
                                    services.historyLogger.removeApp(screen.appPubKey)
                                    currentScreen = Screen.ConnectedApps
                                    refreshConnectedApps()
                                }
                            },
                            onCopyUri = { copyToClipboard(it) },
                            onBack = { currentScreen = Screen.ConnectedApps },
                        )
                    }
                    Screen.Activity -> {
                        var history by remember { mutableStateOf(emptyList<com.greenart7c3.nostrsigner.desktop.data.HistoryRow>()) }
                        LaunchedEffect(Unit) { history = services.historyLogger.recentHistory() }
                        ActivityScreen(history)
                    }
                    Screen.Settings -> SettingsScreen(
                        relays = relayList,
                        onAddRelay = { url ->
                            scope.launch {
                                services.relayStore.add(url)
                                relayList = services.relayStore.list()
                            }
                        },
                        onRemoveRelay = { url ->
                            scope.launch {
                                services.relayStore.remove(url)
                                relayList = services.relayStore.list()
                            }
                        },
                        relayValidator = { RelayUrlNormalizer.normalizeOrNull(it) != null },
                        dataDirPath = AppDataDir.directory.absolutePath,
                        onRevealDataDir = {
                            scope.launch(Dispatchers.IO) {
                                runCatching { Desktop.getDesktop().open(AppDataDir.directory) }
                            }
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            scope.launch { services.settingsStore.set("theme_mode", mode.name) }
                        },
                    )
                }
            }
        }

        if (showAccountSwitcher) {
            AccountSwitcherDialog(
                accounts = accounts,
                currentPubKeyHex = pubKeyHex,
                onSelect = { selected ->
                    showAccountSwitcher = false
                    onSwitchAccount(selected)
                },
                onAddAccount = {
                    showAccountSwitcher = false
                    onAddAccount()
                },
                onLogout = {
                    showAccountSwitcher = false
                    showLogoutConfirm = true
                },
                onDismiss = { showAccountSwitcher = false },
            )
        }

        if (showLogoutConfirm) {
            ConfirmDialog(
                title = "Log out of this account?",
                message = "This permanently deletes the locally-stored key for ${pubKeyHex.shortenHex()} from this device. " +
                    "Make sure you have it backed up elsewhere — it cannot be recovered from Amber Bunker afterward.",
                confirmLabel = "Log out",
                onConfirm = {
                    showLogoutConfirm = false
                    onLogout(pubKeyHex)
                },
                onCancel = { showLogoutConfirm = false },
            )
        }
    }
}

@Composable
private fun NavRail(currentScreen: Screen, pendingCount: Int, onNavigate: (Screen) -> Unit) {
    NavigationRail {
        NavigationRailItem(
            selected = currentScreen == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
            icon = {
                if (pendingCount > 0) {
                    BadgedBox(badge = { Badge { Text("$pendingCount") } }) { Icon(Icons.Default.Home, contentDescription = "Home") }
                } else {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
            },
            label = { Text("Home") },
        )
        NavigationRailItem(
            selected = currentScreen == Screen.Connect,
            onClick = { onNavigate(Screen.Connect) },
            icon = { Icon(Icons.Default.Link, contentDescription = "Connect") },
            label = { Text("Connect") },
        )
        NavigationRailItem(
            selected = currentScreen == Screen.ConnectedApps || currentScreen is Screen.AppDetail,
            onClick = { onNavigate(Screen.ConnectedApps) },
            icon = { Icon(Icons.Default.Apps, contentDescription = "Connected apps") },
            label = { Text("Apps") },
        )
        NavigationRailItem(
            selected = currentScreen == Screen.Activity,
            onClick = { onNavigate(Screen.Activity) },
            icon = { Icon(Icons.Default.History, contentDescription = "Activity") },
            label = { Text("Activity") },
        )
        NavigationRailItem(
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}
