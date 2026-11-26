package com.greenart7c3.nostrsigner.ui.components

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.proxy
import com.greenart7c3.nostrsigner.commonsui.proxy_is_connected
import com.greenart7c3.nostrsigner.commonsui.proxy_is_not_working
import com.greenart7c3.nostrsigner.commonsui.reconnect
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.IncomingRequests
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalRelayClient
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.NavBackStackEntryWrapper
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.routes
import com.greenart7c3.nostrsigner.ui.navigation.title
import com.greenart7c3.nostrsigner.ui.theme.connectedColorDark
import com.greenart7c3.nostrsigner.ui.theme.connectedColorLight
import com.greenart7c3.nostrsigner.ui.theme.isAmberDarkTheme
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import java.util.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val contentTitledRoutes = listOf("Permission/", "Activity/", "RelayLogScreen/", "qrcode/")

private val routesWithoutTopBar = setOf("login", "create", "loginPage")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmberTopAppBar(
    destinationRoute: String,
    navBackStackEntry: NavBackStackEntryWrapper,
    account: Account,
    requests: IncomingRequests,
    modifier: Modifier = Modifier,
    viewModel: TopAppBarViewModel = viewModel(factory = TopAppBarViewModel.factory(LocalAccountRepository.current)),
) {
    val showsIntents = requests.intents.isNotEmpty() && requests.packageName != null && destinationRoute == Route.IncomingRequest.route
    if (showsIntents || destinationRoute in routesWithoutTopBar) return

    CenterAlignedTopAppBar(
        actions = { TopBarStatus() },
        title = {
            TopBarTitle(
                destinationRoute = destinationRoute,
                navBackStackEntry = navBackStackEntry,
                npub = account.npub,
                bunkerRequests = requests.bunkerRequests,
                savedApp = viewModel::savedApp,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun TopBarStatus() {
    val relayClient = LocalRelayClient.current
    val settingsStore = LocalSettingsStore.current
    val relayStats by relayClient.stats.relayStatus.collectAsStateWithLifecycle(Pair(emptySet(), emptySet()))
    if (relayStats.first.isEmpty() && relayStats.second.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RelayStatusButton(connected = relayStats.second.size, total = relayStats.first.size)

        if (settingsStore.settings.useProxy) {
            ProxyStatusButton()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelayStatusButton(
    connected: Int,
    total: Int,
) {
    val relayClient = LocalRelayClient.current
    val appScope = LocalAppScope.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(text = getStringBlocking(Res.string.reconnect))
            }
        },
        state = rememberTooltipState(),
    ) {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    onClick = {
                        appScope.launch {
                            relayClient.reconnect()
                        }
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$connected/$total")
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.relays),
                contentDescription = getStringBlocking(Res.string.reconnect),
                tint = LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun rememberProxyWorks(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsStore = LocalSettingsStore.current
    val relayClient = LocalRelayClient.current
    val appScope = LocalAppScope.current
    val torIsRunning by TorManager.isRunning.collectAsStateWithLifecycle()
    var orbotProxyEnabled by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                -> {
                    if (settingsStore.settings.torMode != TorMode.BUILTIN) {
                        appScope.launch {
                            orbotProxyEnabled = relayClient.isSocksProxyAlive("127.0.0.1", settingsStore.settings.proxyPort)
                        }
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return if (settingsStore.settings.torMode == TorMode.BUILTIN) torIsRunning else orbotProxyEnabled
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyStatusButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isProxyEnabled = rememberProxyWorks()
    val status = if (isProxyEnabled) Res.string.proxy_is_connected else Res.string.proxy_is_not_working

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(text = getStringBlocking(status))
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    Toast.makeText(context, getStringBlocking(status), Toast.LENGTH_SHORT).show()
                }
            },
            content = {
                Icon(
                    Icons.Outlined.Shield,
                    getStringBlocking(Res.string.proxy),
                    tint = if (isProxyEnabled) {
                        if (isAmberDarkTheme()) connectedColorDark else connectedColorLight
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            },
        )
    }
}

@Composable
private fun TopBarTitle(
    destinationRoute: String,
    navBackStackEntry: NavBackStackEntryWrapper,
    npub: String,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    savedApp: suspend (npub: String, key: String) -> SavedAppName?,
) {
    val currentSavedApp by rememberUpdatedState(savedApp)
    val configuration = LocalConfiguration.current
    var title by remember(destinationRoute, configuration) {
        mutableStateOf(routes.find { it.route.startsWith(destinationRoute) }?.title() ?: "")
    }
    LaunchedEffect(destinationRoute, configuration) {
        launch(Dispatchers.IO) {
            if (contentTitledRoutes.any { destinationRoute.startsWith(it) }) {
                val arguments = navBackStackEntry.navBackStackEntry?.arguments
                arguments?.getString("content")?.let {
                    title = Route.QrCode.title()
                }
                for (argument in listOf("packageName", "key")) {
                    arguments?.getString(argument)?.let { key ->
                        title = savedAppTitle(currentSavedApp(npub, key), key, destinationRoute)
                    }
                }
                arguments?.getString("url")?.let { url ->
                    title = Base64.getDecoder().decode(url).toString(Charsets.UTF_8)
                }
            } else if (destinationRoute == Route.IncomingRequest.route && bunkerRequests.isNotEmpty()) {
                title = bunkerRequestTitle(bunkerRequests.first(), npub, currentSavedApp).ifBlank {
                    routes.find { it.route == destinationRoute }?.title() ?: ""
                }
            } else {
                title = routes.find { it.route == destinationRoute }?.title() ?: ""
            }
        }
    }

    Text(title)
}

private fun savedAppTitle(
    application: SavedAppName?,
    key: String,
    destinationRoute: String,
): String {
    val name = application?.name?.ifBlank { application.key.toShortenHex() } ?: key
    return if (destinationRoute.startsWith("Activity/")) {
        "$name - ${routes.find { it.route.startsWith(destinationRoute) }?.title()}"
    } else {
        name
    }
}

private suspend fun bunkerRequestTitle(
    request: AmberBunkerRequest,
    npub: String,
    savedApp: suspend (npub: String, key: String) -> SavedAppName?,
): String {
    var application = savedApp(npub, request.localKey)
    if (application == null && request.request is BunkerRequestConnect) {
        val secret = request.request.secret
        if (secret != null) {
            application = savedApp(npub, secret)
        }
    }
    return application?.name?.ifBlank { request.name.ifBlank { application.key.toShortenHex() } } ?: request.name
}

@Composable
fun rememberAppDisplayInfo(packageName: String): AppDisplayInfo {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            AppDisplayInfo(
                name = sanitizeAppName(context.packageManager.getApplicationLabel(appInfo).toString()),
                icon = context.packageManager.getApplicationIcon(appInfo),
            )
        }.getOrNull() ?: AppDisplayInfo(name = packageName, icon = null)
    }
}

/** Resolves a native app's launcher icon, or null when it can't be queried. */
@Composable
fun rememberAppIcon(packageName: String?): Drawable? {
    if (packageName.isNullOrBlank()) return null
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }
}

data class AppDisplayInfo(
    val name: String,
    val icon: Drawable? = null,
)
