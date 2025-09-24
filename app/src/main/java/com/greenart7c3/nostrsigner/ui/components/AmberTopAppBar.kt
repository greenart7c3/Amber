package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.routes
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmberTopAppBar(
    destinationRoute: String,
    lifecycleOwner: LifecycleOwner,
    scope: CoroutineScope,
    context: Context,
    navBackStackEntry: NavBackStackEntry?,
    account: Account,
    intents: List<IntentData>,
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
) {
    if (destinationRoute != "login" && destinationRoute != "create" && destinationRoute != "loginPage") {
        CenterAlignedTopAppBar(
            actions = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(text = context.getString(R.string.reconnect))
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                Amber.instance.applicationIOScope.launch {
                                    Amber.instance.reconnect()
                                }
                            },
                            content = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val relayStats = Amber.instance.client.relayStatusFlow().collectAsStateWithLifecycle()
                                    Text("${relayStats.value.connected.size}/${relayStats.value.available.size}")
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.relays),
                                        contentDescription = context.getString(R.string.reconnect),
                                        tint = Color.Unspecified,
                                    )
                                }
                            },
                        )
                    }

                    if (Amber.instance.settings.useProxy) {
                        var isProxyEnabled by remember { mutableStateOf(false) }
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_START -> {
                                        Amber.instance.applicationIOScope.launch {
                                            isProxyEnabled = Amber.instance.isSocksProxyAlive("127.0.0.1", Amber.instance.settings.proxyPort)
                                        }
                                    }

                                    Lifecycle.Event.ON_RESUME -> {
                                        Amber.instance.applicationIOScope.launch {
                                            isProxyEnabled = Amber.instance.isSocksProxyAlive("127.0.0.1", Amber.instance.settings.proxyPort)
                                        }
                                    }

                                    else -> {}
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)

                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Text(text = if (isProxyEnabled) context.getString(R.string.proxy_is_connected) else context.getString(R.string.proxy_is_not_working))
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (isProxyEnabled) {
                                            Toast.makeText(context, context.getString(R.string.proxy_is_connected), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.proxy_is_not_working), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                content = {
                                    Icon(
                                        Icons.Outlined.Shield,
                                        context.getString(R.string.proxy),
                                        tint = if (isProxyEnabled) Color.Green else Color.Red,
                                    )
                                },
                            )
                        }
                    }
                }
            },
            title = {
                var title by remember { mutableStateOf(routes.find { it.route.startsWith(destinationRoute) }?.title ?: "") }
                LaunchedEffect(destinationRoute) {
                    if (destinationRoute.startsWith("Permission/") || destinationRoute.startsWith("Activity/") || destinationRoute.startsWith("RelayLogScreen/") || destinationRoute.startsWith("qrcode/")) {
                        launch(Dispatchers.IO) {
                            navBackStackEntry?.arguments?.getString("content")?.let {
                                title = Route.QrCode.title
                            }
                            navBackStackEntry?.arguments?.getString("packageName")?.let { packageName ->
                                val application = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(packageName)?.application
                                title = if (destinationRoute.startsWith("Activity/")) {
                                    "${application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName} - ${routes.find { it.route.startsWith(destinationRoute) }?.title}"
                                } else {
                                    application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName
                                }
                            }
                            navBackStackEntry?.arguments?.getString("key")?.let { packageName ->
                                val application = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(packageName)?.application
                                title = if (destinationRoute.startsWith("Activity/")) {
                                    "${application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName} - ${routes.find { it.route.startsWith(destinationRoute) }?.title}"
                                } else {
                                    application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName
                                }
                            }
                            navBackStackEntry?.arguments?.getString("url")?.let { url ->
                                val localUrl = Base64.getDecoder().decode(url).toString(Charsets.UTF_8)
                                title = localUrl
                            }
                        }
                    } else {
                        launch(Dispatchers.IO) {
                            if (destinationRoute == Route.IncomingRequest.route && (intents.isNotEmpty() || bunkerRequests.isNotEmpty())) {
                                val key = if (bunkerRequests.isNotEmpty()) {
                                    bunkerRequests.first().localKey
                                } else {
                                    packageName
                                }

                                val application = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key ?: "")?.application
                                val titleTemp = application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName ?: ""
                                title = if (titleTemp.isBlank()) {
                                    routes.find { it.route == destinationRoute }?.title ?: ""
                                } else {
                                    "$titleTemp - Request"
                                }
                            } else {
                                title = routes.find { it.route == destinationRoute }?.title ?: ""
                            }
                        }
                    }
                }

                Text(title)
            },
        )
    }
}
