package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.PermissionsFloatingActionButton
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toHexKey
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    database: AppDatabase,
    navController: NavController,
    destinationRoute: String,
    shouldShowFloatingButton: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val items = mutableListOf(Route.Applications, Route.IncomingRequest, Route.ActiveRelays, Route.Settings, Route.Accounts)
    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "offline") {
        items.remove(Route.ActiveRelays)
    }
    var shouldShowBottomSheet by remember { mutableStateOf(false) }
    val sheetState =
        rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
            skipPartiallyExpanded = true,
        )

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
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "offline" && shouldShowFloatingButton) {
                PermissionsFloatingActionButton(
                    accountStateViewModel,
                    account,
                ) {
                    val secret = UUID.randomUUID().toString().substring(0, 6)
                    scope.launch(Dispatchers.IO) {
                        val application =
                            ApplicationEntity(
                                secret,
                                "",
                                NostrSigner.getInstance().settings.defaultRelays,
                                "",
                                "",
                                "",
                                account.keyPair.pubKey.toHexKey(),
                                false,
                                secret,
                                false,
                                account.signPolicy,
                            )

                        database.applicationDao().insertApplication(
                            application,
                        )
                        val relayString = NostrSigner.getInstance().settings.defaultRelays.joinToString(separator = "&") { "relay=${it.url}" }
                        val bunkerUrl = "bunker://${account.keyPair.pubKey.toHexKey()}?$relayString"
                        clipboardManager.setText(AnnotatedString(bunkerUrl))
                        scope.launch(Dispatchers.Main) {
                            navController.navigate("Permission/$secret")
                        }
                    }
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val title = items.find { it.route == destinationRoute }?.title ?: ""
                    Text(title)
                },
            )
        },
        bottomBar = {
            NavigationBar(tonalElevation = 0.dp) {
                items.forEach {
                    val selected = destinationRoute == it.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (it.route == Route.Accounts.route) {
                                scope.launch {
                                    sheetState.show()
                                    shouldShowBottomSheet = true
                                }
                            } else {
                                navController.navigate(it.route) {
                                    popUpTo(0)
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) it.selectedIcon else it.icon,
                                it.route,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        content(padding)
    }
}
