package com.greenart7c3.nostrsigner.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.delay

fun sendResult(
    context: Context,
    packageName: String?,
    account: Account,
    key: String,
    rememberChoice: Boolean,
    clipboardManager: ClipboardManager,
    event: String,
    id: String,
    value: String
) {
    val activity = context.getAppCompatActivity()
    if (packageName != null) {
        if (rememberChoice) {
            account.savedApps[key] = rememberChoice
        }
        LocalPreferences.saveToEncryptedStorage(account)
        val intent = Intent()
        intent.putExtra("signature", value)
        intent.putExtra("id", id)
        intent.putExtra("event", event)
        activity?.setResult(RESULT_OK, intent)
    } else {
        clipboardManager.setText(AnnotatedString(value))
        Toast.makeText(
            context,
            context.getString(R.string.signature_copied_to_the_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }
    activity?.finish()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(account: Account, accountStateViewModel: AccountStateViewModel, json: IntentData?, packageName: String?, route: String?) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val items = listOf(Route.Home, Route.Permissions, Route.Settings)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(account.keyPair.pubKey.toNpub().toShortenHex())
                }
            )
        },
        bottomBar = {
            if (navBackStackEntry?.destination?.route?.contains("Permission/") == false) {
                NavigationBar {
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
            startDestination = Route.Home.route,
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

    LaunchedEffect(Unit) {
        if (route !== null) {
            delay(200)
            try {
                navController.navigate(route) {
                    popUpTo(0)
                }
            } catch (_: Exception) { }
        }
    }
}
