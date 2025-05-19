package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
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
    packageName: String?,
) {
    if (destinationRoute != "login" && destinationRoute != "create" && destinationRoute != "loginPage") {
        CenterAlignedTopAppBar(
            actions = {
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

                    Icon(
                        Icons.Outlined.Shield,
                        "Proxy",
                        tint = if (isProxyEnabled) Color.Green else Color.Red,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                scope.launch {
                                    if (isProxyEnabled) {
                                        Toast.makeText(context, context.getString(R.string.proxy_is_connected), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.proxy_is_not_working), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLongClick = {
                                scope.launch {
                                    if (isProxyEnabled) {
                                        Toast.makeText(context, context.getString(R.string.proxy_is_connected), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.proxy_is_not_working), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ),
                    )
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
                            if (destinationRoute == Route.IncomingRequest.route && intents.isNotEmpty()) {
                                val application = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(intents.first().bunkerRequest?.localKey ?: packageName ?: "")?.application
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
