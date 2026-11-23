package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.could_not_connect_to_tor
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupScreen
import com.greenart7c3.nostrsigner.ui.actions.ActiveRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.ActivityScreen
import com.greenart7c3.nostrsigner.ui.actions.CloudBackupScreen
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotScreen
import com.greenart7c3.nostrsigner.ui.actions.DefaultRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.ExportAllAccountsScreen
import com.greenart7c3.nostrsigner.ui.actions.QrCodeScreen
import com.greenart7c3.nostrsigner.ui.actions.RelayLogScreen
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import java.util.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What the main destinations are laid out with: the scaffold's [padding] and the side [margin]. */
@Immutable
internal class MainLayout(val padding: PaddingValues, val margin: Dp) {
    val topPadding: Dp get() = padding.calculateTopPadding() + margin * 1.5f

    fun listPadding(): PaddingValues = PaddingValues(
        top = topPadding,
        bottom = padding.calculateBottomPadding(),
        start = padding.calculateStartPadding(LayoutDirection.Ltr) + margin,
        end = padding.calculateEndPadding(LayoutDirection.Ltr) + margin,
    )
}

internal fun Modifier.fixedScreen(layout: MainLayout): Modifier = fillMaxSize().padding(layout.padding).padding(horizontal = layout.margin).padding(top = layout.margin * 1.5f)

internal fun Modifier.fixedScreenInsets(layout: MainLayout): Modifier = fillMaxSize()
    .padding(layout.padding)
    .consumeWindowInsets(layout.padding)
    .padding(horizontal = layout.margin)
    .padding(top = layout.margin * 1.5f)

internal fun Modifier.scrollingScreen(layout: MainLayout, scrollState: ScrollState): Modifier = fillMaxSize()
    .padding(layout.padding)
    .verticalScrollbar(scrollState)
    .verticalScroll(scrollState)
    .padding(horizontal = layout.margin)
    .padding(top = layout.margin * 1.5f)

internal fun Modifier.scrollingScreenAboveKeyboard(layout: MainLayout, scrollState: ScrollState): Modifier = fillMaxSize()
    .padding(layout.padding)
    .consumeWindowInsets(layout.padding)
    .verticalScrollbar(scrollState)
    .verticalScroll(scrollState)
    .padding(horizontal = layout.margin)
    .padding(top = layout.margin * 1.5f)
    .imePadding()

/**
 * The requests the main screen shows: the queued NIP-55 intents and NIP-46 bunker requests,
 * the app that sent them ([packageName], [appName]; null for bunker requests) and whether
 * another app opened Amber for them.
 */
@Immutable
class IncomingRequests(
    val intents: ImmutableList<IntentData>,
    val bunkerRequests: ImmutableList<AmberBunkerRequest>,
    val packageName: String?,
    val appName: String?,
    val isExternalRequest: Boolean,
)

internal fun NavGraphBuilder.loginDestinations(
    navController: NavHostControllerWrapper,
    onOpenFirstAccount: suspend () -> Unit,
    onCreate: suspend (ConnectionSetup, Set<String>, String) -> Unit,
    onLogin: suspend (KeyPair, ConnectionSetup) -> Unit,
    onSignedUp: () -> Unit,
) {
    composable("login") { MainPage(navHostControllerWrapper = navController, onOpenFirstAccount = onOpenFirstAccount) }
    composable("create") {
        SignUpPage(
            navHostControllerWrapper = navController,
            onCreate = onCreate,
            onFinish = {
                onSignedUp()
                navController.navController.navigate(Route.Applications.route) { popUpTo(0) }
            },
        )
    }
    composable("loginPage") {
        LoginPage(
            navHostControllerWrapper = navController,
            onLogin = onLogin,
            onFinish = { navController.navController.navigate(Route.Applications.route) { popUpTo(0) } },
        )
    }
}

internal fun NavGraphBuilder.requestDestination(
    layout: MainLayout,
    account: Account,
    requests: IncomingRequests,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    composable(Route.IncomingRequest.route) {
        val scrollState = rememberScrollState()
        IncomingRequestScreen(
            horizontalPadding = layout.margin,
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.padding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = layout.margin),
            scaffoldPadding = layout.padding,
            requests = requests,
            account = account,
            onRemoveIntentData = onRemoveIntentData,
            onLoading = onLoading,
        )
    }
}

internal fun NavGraphBuilder.accountDestinations(layout: MainLayout, account: Account, navController: NavHostControllerWrapper) {
    composable(Route.QrCode.route, arguments = listOf(navArgument("content") { type = NavType.StringType })) {
        it.arguments?.getString("content")?.let { content -> QrCodeScreen(modifier = Modifier.fixedScreen(layout), content = content) }
    }
    composable(Route.Applications.route) { ApplicationsScreen(modifier = Modifier.fixedScreen(layout), account = account, navControllerWrapper = navController) }
    composable(Route.Settings.route) {
        SettingsScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState()), account = account, onNav = { navController.navController.navigate(it) })
    }
    composable(Route.AccountBackup.route) {
        val appScope = LocalAppScope.current
        AccountBackupScreen(
            modifier = Modifier.scrollingScreenAboveKeyboard(layout, rememberScrollState()),
            navController = navController.navController,
            onShowQrCode = {
                appScope.launch(Dispatchers.Main) { navController.navController.navigate(Route.QrCode.route.replace("{content}", it)) }
            },
        )
    }
    composable(Route.ExportAllAccounts.route) { ExportAllAccountsScreen(Modifier.scrollingScreenAboveKeyboard(layout, rememberScrollState())) }
    composable(Route.CloudBackup.route) { CloudBackupScreen(Modifier.scrollingScreenAboveKeyboard(layout, rememberScrollState())) }
    composable(Route.Permission.route, arguments = listOf(navArgument("packageName") { type = NavType.StringType })) {
        it.arguments?.getString("packageName")?.let { packageName ->
            EditPermission(
                modifier = Modifier.scrollingScreenAboveKeyboard(layout, rememberScrollState()),
                account = account,
                selectedPackage = packageName,
                navController = navController.navController,
            )
        }
    }
}

internal fun NavGraphBuilder.relayDestinations(layout: MainLayout, account: Account, navController: NavHostControllerWrapper) {
    composable(Route.ActiveRelays.route) {
        ActiveRelaysScreen(navController = navController.navController, account = account, modifier = Modifier.scrollingScreen(layout, rememberScrollState()))
    }
    composable(Route.DefaultRelays.route) { DefaultRelaysScreen(account = account, modifier = Modifier.fixedScreen(layout)) }
    composable(Route.RelaysScreen.route) { RelaysScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState()), navController = navController.navController) }
    composable(Route.DefaultProfileRelaysScreen.route) { DefaultProfileRelaysScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState()), account = account) }
    composable(Route.RelayLogScreen.route, arguments = listOf(navArgument("url") { type = NavType.StringType })) {
        it.arguments?.getString("url")?.let { url ->
            val localUrl = Base64.getDecoder().decode(url).toString(Charsets.UTF_8)
            RelayLogScreen(url = localUrl, account = account, paddingValues = layout.listPadding())
        }
    }
    composable(Route.AuthWhitelist.route) { AuthWhitelistScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState())) }
    composable(Route.Logs.route) { LogsScreen(layout.listPadding(), account = account) }
}

internal fun NavGraphBuilder.applicationDestinations(layout: MainLayout, account: Account, navController: NavHostControllerWrapper) {
    composable(Route.NewApplication.route) {
        NewApplicationScreen(account = account, navController = navController.navController, modifier = Modifier.scrollingScreen(layout, rememberScrollState()))
    }
    composable(Route.NewNsecBunker.route) {
        val scrollState = rememberScrollState()
        NewNsecBunkerScreen(
            account = account,
            navController = navController.navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.padding)
                .consumeWindowInsets(layout.padding)
                .imePadding()
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = layout.margin)
                .padding(top = layout.margin * 1.5f),
        )
    }
    composable(Route.NSecBunkerCreated.route, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
        it.arguments?.getString("key")?.let { key ->
            NewNsecBunkerCreatedScreen(account = account, key = key, modifier = Modifier.scrollingScreen(layout, rememberScrollState()))
        }
    }
    composable(Route.EditConfiguration.route, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
        it.arguments?.getString("key")?.let { key ->
            EditConfigurationScreen(
                modifier = Modifier.scrollingScreen(layout, rememberScrollState()).imePadding(),
                key = key,
                account = account,
                onNav = { route -> navController.navController.navigate(route) { popUpTo(0) } },
            )
        }
    }
    composable(Route.Activity.route, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
        it.arguments?.getString("key")?.let { key ->
            ActivityScreen(
                account = account,
                key = key,
                paddingValues = layout.listPadding(),
                topPadding = layout.topPadding,
                modifier = Modifier.fillMaxSize().imePadding(),
            )
        }
    }
    composable(Route.Activities.route) {
        ActivitiesScreen(account = account, paddingValues = layout.listPadding(), topPadding = layout.topPadding, modifier = Modifier.fillMaxSize().imePadding())
    }
}

internal fun NavGraphBuilder.securityDestinations(layout: MainLayout, account: Account, navController: NavHostControllerWrapper) {
    composable(Route.Language.route) { LanguageScreen(modifier = Modifier.fixedScreen(layout)) }
    composable(Route.SignPolicy.route) {
        SignPolicySettingsScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState()), account = account, navController = navController.navController)
    }
    composable(Route.Security.route) { SecurityScreen(modifier = Modifier.fixedScreen(layout), navController = navController.navController) }
    composable(Route.SetupPin.route) { SetupPinScreen(modifier = Modifier.fixedScreen(layout), navController = navController.navController) }
    composable(Route.ConfirmPin.route, arguments = listOf(navArgument("pin") { type = NavType.StringType })) {
        it.arguments?.getString("pin")?.let { pin -> ConfirmPinScreen(modifier = Modifier.fixedScreen(layout), pin = pin, navController = navController.navController) }
    }
    composable(Route.UpdateSettings.route) { UpdateSettingsScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState())) }
    composable(Route.ServiceSettings.route) { ServiceSettingsScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState())) }
    composable(Route.ApplicationsBackup.route) { ApplicationsBackupScreen(modifier = Modifier.scrollingScreen(layout, rememberScrollState())) }
}

internal fun NavGraphBuilder.profileDestinations(
    layout: MainLayout,
    navController: NavHostControllerWrapper,
    onUseOrbot: (port: Int, onDone: () -> Unit) -> Unit,
    onUseBuiltinTor: (onDone: () -> Unit) -> Unit,
    onSaveProfile: () -> Unit,
) {
    val toSettings = { navController.navController.navigate(Route.Settings.route) { popUpTo(0) } }
    composable(Route.TorSettings.route) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        ConnectOrbotScreen(
            modifier = Modifier.scrollingScreen(layout, rememberScrollState()),
            onPost = { onUseOrbot(it, toSettings) },
            onBuiltinTor = { onUseBuiltinTor(toSettings) },
            onError = {
                scope.launch { Toast.makeText(context, getStringBlocking(Res.string.could_not_connect_to_tor), Toast.LENGTH_SHORT).show() }
            },
        )
    }
    composable(Route.EditProfile.route, arguments = listOf(navArgument("key") { type = NavType.StringType })) {
        it.arguments?.getString("key")?.let { key -> EditProfileScreen(npub = key, onSave = onSaveProfile, modifier = Modifier.scrollingScreen(layout, rememberScrollState())) }
    }
}

internal fun NavGraphBuilder.reportDestinations(layout: MainLayout, account: Account, navController: NavHostControllerWrapper, onLoading: (Boolean) -> Unit) {
    val goBack: () -> Unit = { navController.navController.navigateUp() }
    composable(Route.Feedback.route) {
        val appScope = LocalAppScope.current
        FeedbackScreen(
            modifier = Modifier.fixedScreenInsets(layout).imePadding(),
            account = account,
            onDismiss = { appScope.launch(Dispatchers.Main) { goBack() } },
            onLoading = onLoading,
        )
    }
    composable(Route.CrashReport.route) {
        val appScope = LocalAppScope.current
        CrashReportScreen(modifier = Modifier.fixedScreenInsets(layout), account = account, onDismiss = { appScope.launch(Dispatchers.Main) { goBack() } }, onLoading = onLoading)
    }
    composable(Route.TranslationReport.route) {
        val appScope = LocalAppScope.current
        TranslationReportScreen(
            modifier = Modifier.fixedScreenInsets(layout),
            account = account,
            onDismiss = { appScope.launch(Dispatchers.Main) { goBack() } },
            onLoading = onLoading,
        )
    }
}
