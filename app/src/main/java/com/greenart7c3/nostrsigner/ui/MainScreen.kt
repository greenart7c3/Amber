package com.greenart7c3.nostrsigner.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

fun sendResult(
    context: Context,
    packageName: String?,
    account: Account,
    key: String,
    rememberChoice: Boolean,
    clipboardManager: ClipboardManager,
    event: String,
    value: String,
    intentData: IntentData,
    permissions: List<Permission>? = null
) {
    val activity = context.getAppCompatActivity()
    if (packageName != null) {
        permissions?.filter { it.checked }?.forEach {
            val type = it.type.toUpperCase(Locale.current)
            val permissionKey = if (it.type == "sign_event") "$packageName-$type-${it.kind}" else "$packageName-$type"
            account.savedApps[permissionKey] = true
        }

        if (rememberChoice) {
            account.savedApps[key] = true
        }

        LocalPreferences.saveToEncryptedStorage(account)
        val intent = Intent()
        intent.putExtra("signature", value)
        intent.putExtra("id", intentData.id)
        intent.putExtra("event", event)
        if (intentData.type == SignerType.GET_PUBLIC_KEY) {
            intent.putExtra("package", BuildConfig.APPLICATION_ID)
        }
        activity?.setResult(RESULT_OK, intent)
    } else if (intentData.callBackUrl != null) {
        if (intentData.returnType == ReturnType.SIGNATURE) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(intentData.callBackUrl + Uri.encode(value))
            context.startActivity(intent)
        } else {
            if (intentData.compression == CompressionType.GZIP) {
                // Compress the string using GZIP
                val byteArrayOutputStream = ByteArrayOutputStream()
                val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
                gzipOutputStream.write(event.toByteArray())
                gzipOutputStream.close()

                // Convert the compressed data to Base64
                val compressedData = byteArrayOutputStream.toByteArray()
                val encodedString = Base64.getEncoder().encodeToString(compressedData)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(intentData.callBackUrl + Uri.encode("Signer1$encodedString"))
                context.startActivity(intent)
            } else {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(intentData.callBackUrl + Uri.encode(event))
                context.startActivity(intent)
            }
        }
    } else {
        val result = if (intentData.returnType == ReturnType.SIGNATURE) {
            value
        } else {
            event
        }
        val message = if (intentData.returnType == ReturnType.SIGNATURE) {
            context.getString(R.string.signature_copied_to_the_clipboard)
        } else {
            context.getString(R.string.event_copied_to_the_clipboard)
        }

        clipboardManager.setText(AnnotatedString(result))

        Toast.makeText(
            context,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
    activity?.finish()
}

@Composable
fun GoToTop(goToTop: () -> Unit) {
    FloatingActionButton(
        onClick = goToTop,
        shape = CircleShape
    ) {
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = stringResource(R.string.go_to_top)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    json: IntentData?,
    packageName: String?,
    appName: String?,
    route: String?
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val items = listOf(Route.Home, Route.Permissions, Route.Settings)
    var shouldShowBottomSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val interactionSource = remember { MutableInteractionSource() }

    Scaffold(
        floatingActionButton = {
            if (navBackStackEntry?.destination?.route?.contains("History") == true) {
                GoToTop {
                    scope.launch {
                        listState.scrollToItem(0)
                    }
                }
            }
        },
        topBar = {
            if (shouldShowBottomSheet) {
                AccountsBottomSheet(
                    sheetState = sheetState,
                    account = account,
                    accountStateViewModel = accountStateViewModel,
                    onClose = {
                        scope.launch {
                            shouldShowBottomSheet = false
                            sheetState.hide()
                        }
                    }
                )
            }

            CenterAlignedTopAppBar(
                title = {
                    val name = LocalPreferences.getAccountName(account.keyPair.pubKey.toNpub())
                    Row(
                        Modifier
                            .border(
                                border = ButtonDefaults.outlinedButtonBorder,
                                shape = ButtonBorder
                            )
                            .padding(8.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                scope.launch {
                                    sheetState.show()
                                    shouldShowBottomSheet = true
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name.ifBlank { account.keyPair.pubKey.toNpub().toShortenHex() })
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (navBackStackEntry?.destination?.route?.contains("Permission/") == false) {
                NavigationBar(tonalElevation = 0.dp) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    items.forEach {
                        val selected = currentRoute == it.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(it.route) {
                                    popUpTo(0)
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) it.selectedIcon else it.icon,
                                    it.route
                                )
                            },
                            label = {
                                Text(it.route)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController,
            startDestination = route ?: Route.Home.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(
                Route.Home.route,
                content = {
                    HomeScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        json,
                        packageName,
                        appName,
                        account
                    )
                }
            )

            composable(
                Route.Permissions.route,
                content = {
                    PermissionsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController
                    )
                }
            )

            composable(
                Route.Settings.route,
                content = {
                    SettingsScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        accountStateViewModel,
                        account
                    )
                }
            )

            composable(
                Route.Permission.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                content = {
                    EditPermission(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        selectedPackage = it.arguments?.getString("packageName")!!,
                        navController = navController
                    )
                }
            )
        }
    }
}

fun timeAgoShort(mills: Long?, stringForNow: String): String {
    if (mills == null) return " "

    var humanReadable = DateUtils.getRelativeTimeSpanString(
        mills * 1000,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_ALL
    ).toString()
    if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) {
        humanReadable = stringForNow
    }

    return humanReadable
}
