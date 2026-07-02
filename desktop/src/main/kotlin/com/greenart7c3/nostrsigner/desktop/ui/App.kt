package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex

sealed class Route(val title: String, val icon: ImageVector) {
    data object IncomingRequest : Route("Incoming request", Icons.Default.Notifications)
    data object Applications : Route("Applications", Icons.Default.Apps)
    data object Relays : Route("Relays", Icons.Default.CellTower)
    data object Settings : Route("Settings", Icons.Default.Settings)
}

val bottomRoutes = listOf(Route.IncomingRequest, Route.Applications, Route.Relays, Route.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val loading by Session.loading.collectAsState()
    val account by Session.account.collectAsState()
    val addingAccount by Session.addingAccount.collectAsState()
    val pending by AmberDesktop.engine.pending.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentRoute by remember { mutableStateOf<Route>(Route.Applications) }
    // null = list; non-null = detail screen for that application key
    var selectedApplication by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Toaster.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Jump to the approval screen whenever a new request arrives.
    LaunchedEffect(pending.size) {
        if (pending.isNotEmpty()) {
            currentRoute = Route.IncomingRequest
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
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    val name by acc.name.collectAsState()
                    Text(
                        if (selectedApplication != null) {
                            "Permissions"
                        } else {
                            "${currentRoute.title} — ${name.ifBlank { acc.npub.toShortenHex() }}"
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                bottomRoutes.forEach { route ->
                    NavigationBarItem(
                        selected = currentRoute == route && selectedApplication == null,
                        onClick = {
                            selectedApplication = null
                            currentRoute = route
                        },
                        icon = {
                            if (route == Route.IncomingRequest && pending.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${pending.size}") } }) {
                                    Icon(route.icon, route.title)
                                }
                            } else {
                                Icon(route.icon, route.title)
                            }
                        },
                        label = { Text(route.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = 900.dp).padding(horizontal = 16.dp)) {
                val selectedApp = selectedApplication
                if (selectedApp != null) {
                    ApplicationDetailScreen(
                        account = acc,
                        appKey = selectedApp,
                        onBack = { selectedApplication = null },
                    )
                } else {
                    when (currentRoute) {
                        Route.IncomingRequest -> IncomingRequestsScreen(account = acc)
                        Route.Applications -> ApplicationsScreen(
                            account = acc,
                            onOpenApplication = { selectedApplication = it },
                        )

                        Route.Relays -> RelaysScreen()
                        Route.Settings -> SettingsScreen(account = acc)
                    }
                }
            }
        }
    }
}
