package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import kotlinx.coroutines.launch

sealed class Route(val title: String, val subtitle: String, val icon: ImageVector) {
    data object IncomingRequest : Route("Incoming requests", "Approve or reject what applications ask this signer to do", Icons.Default.Notifications)
    data object Applications : Route("Applications", "Connected applications and their permissions", Icons.Default.Apps)
    data object Relays : Route("Relays", "Default relays used by bunker connections", Icons.Default.CellTower)
    data object Settings : Route("Settings", "Account, security and application preferences", Icons.Default.Settings)
}

val sidebarRoutes = listOf(Route.IncomingRequest, Route.Applications, Route.Relays, Route.Settings)

@Composable
fun App() {
    val loading by Session.loading.collectAsState()
    val account by Session.account.collectAsState()
    val addingAccount by Session.addingAccount.collectAsState()
    val pending by AmberDesktop.engine.pending.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentRoute by UiState.currentRoute.collectAsState()
    val selectedApplication by UiState.selectedApplication.collectAsState()

    LaunchedEffect(Unit) {
        Toaster.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Jump to the approval screen whenever a new request arrives.
    LaunchedEffect(pending.size) {
        if (pending.isNotEmpty()) {
            UiState.navigate(Route.IncomingRequest)
        }
    }

    val acc = account
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val lockStatus by PassphraseLock.state.collectAsState()
    if (lockStatus == PassphraseLock.Status.LOCKED) {
        UnlockScreen()
        return
    }

    if (acc == null || addingAccount) {
        LoginScreen(
            hasAccounts = acc != null,
            snackbarHostState = snackbarHostState,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Sidebar(
                account = acc,
                currentRoute = currentRoute,
                pendingCount = pending.size,
                onSelect = { UiState.navigate(it) },
            )
            VerticalDivider()

            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                val selectedApp = selectedApplication
                Column(Modifier.widthIn(max = 880.dp)) {
                    if (selectedApp == null) {
                        Text(currentRoute.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            currentRoute.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (selectedApp != null) {
                        ApplicationDetailScreen(
                            account = acc,
                            appKey = selectedApp,
                            onBack = { UiState.selectedApplication.value = null },
                        )
                    } else {
                        when (currentRoute) {
                            Route.IncomingRequest -> IncomingRequestsScreen(account = acc)
                            Route.Applications -> ApplicationsScreen(
                                account = acc,
                                onOpenApplication = { UiState.selectedApplication.value = it },
                            )

                            Route.Relays -> RelaysScreen()
                            Route.Settings -> SettingsScreen(account = acc)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    account: DesktopAccount,
    currentRoute: Route,
    pendingCount: Int,
    onSelect: (Route) -> Unit,
) {
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(
            "Amber",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            "Nostr event signer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))

        sidebarRoutes.forEachIndexed { index, route ->
            SidebarItem(
                route = route,
                selected = route == currentRoute,
                badgeCount = if (route == Route.IncomingRequest) pendingCount else 0,
                shortcut = shortcutLabel("${index + 1}"),
                onClick = { onSelect(route) },
            )
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        AccountSwitcher(account)
    }
}

@Composable
private fun SidebarItem(
    route: Route,
    selected: Boolean,
    badgeCount: Int,
    shortcut: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(route.icon, route.title, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                route.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            if (badgeCount > 0) {
                Badge { Text("$badgeCount") }
            } else {
                Text(
                    shortcut,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * The account chip at the bottom of the sidebar: shows the active account and
 * opens a menu to switch between accounts or add a new one — the desktop
 * equivalent of the mobile account switcher sheet.
 */
@Composable
private fun AccountSwitcher(account: DesktopAccount) {
    val accounts by AccountsStore.accounts.collectAsState()
    val name by account.name.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (name.ifBlank { account.npub.removePrefix("npub1") }).take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "Account" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account.npub.toShortenHex(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.UnfoldMore, "Switch account", Modifier.size(18.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { record ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (record.name.ifBlank { record.npub.toShortenHex() }) +
                                if (record.npub == account.npub) "  ✓" else "",
                        )
                    },
                    onClick = {
                        expanded = false
                        if (record.npub != account.npub) {
                            scope.launch { Session.switchTo(record.npub) }
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add an account") },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    Session.addingAccount.value = true
                },
            )
        }
    }
}
