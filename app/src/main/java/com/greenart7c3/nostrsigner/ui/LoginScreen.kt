package com.greenart7c3.nostrsigner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource as androidStringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.a_nostr_secure_signer
import com.greenart7c3.nostrsigner.commonsui.add_a_key
import com.greenart7c3.nostrsigner.commonsui.amber_github_uri
import com.greenart7c3.nostrsigner.commonsui.amber_is_a_free_and_open_source_project
import com.greenart7c3.nostrsigner.commonsui.check_the_code
import com.greenart7c3.nostrsigner.commonsui.continue_button
import com.greenart7c3.nostrsigner.commonsui.do_not_leave_the_app_until_the_key_is_generated
import com.greenart7c3.nostrsigner.commonsui.encryption_password
import com.greenart7c3.nostrsigner.commonsui.enter_recovery_phrase_description
import com.greenart7c3.nostrsigner.commonsui.enter_strong_password
import com.greenart7c3.nostrsigner.commonsui.generate_a_new_key
import com.greenart7c3.nostrsigner.commonsui.go_back
import com.greenart7c3.nostrsigner.commonsui.hide_password
import com.greenart7c3.nostrsigner.commonsui.import_failed_for_accounts
import com.greenart7c3.nostrsigner.commonsui.key_is_required
import com.greenart7c3.nostrsigner.commonsui.login_mode_private_key
import com.greenart7c3.nostrsigner.commonsui.login_mode_recovery_phrase
import com.greenart7c3.nostrsigner.commonsui.mnemonic_account_index
import com.greenart7c3.nostrsigner.commonsui.mnemonic_account_index_description
import com.greenart7c3.nostrsigner.commonsui.ncryptsec_password
import com.greenart7c3.nostrsigner.commonsui.next
import com.greenart7c3.nostrsigner.commonsui.nickname
import com.greenart7c3.nostrsigner.commonsui.nsec
import com.greenart7c3.nostrsigner.commonsui.offline_previous_install_body
import com.greenart7c3.nostrsigner.commonsui.offline_previous_install_open
import com.greenart7c3.nostrsigner.commonsui.offline_previous_install_title
import com.greenart7c3.nostrsigner.commonsui.ok
import com.greenart7c3.nostrsigner.commonsui.password_is_required
import com.greenart7c3.nostrsigner.commonsui.permissions_and_connection
import com.greenart7c3.nostrsigner.commonsui.please_add_a_nickname_later_you_will_able_to_personalise_your_full_profile_bio_pic_etc_using_your_preferred_nostr_client
import com.greenart7c3.nostrsigner.commonsui.recover_from_backup
import com.greenart7c3.nostrsigner.commonsui.setup_amber_with_your_nostr_private_key_you_can_enter_different_versions_nsec_ncryptsec_or_hex_you_can_also_scan_it_from_a_qr_code
import com.greenart7c3.nostrsigner.commonsui.show_password
import com.greenart7c3.nostrsigner.commonsui.you_will_find_it_in_your_account_section_so_you_don_t_need_to_copy_it_right_now
import com.greenart7c3.nostrsigner.commonsui.your_nostr_account_explainer
import com.greenart7c3.nostrsigner.commonsui.your_nostr_account_is_ready
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberElevatedButton
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.MnemonicLoginInput
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

// The side margin of the login pages: 7% of the screen width.
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun loginPadding(): Dp = LocalConfiguration.current.screenWidthDp.dp * 0.07f

@Composable
fun MainPage(
    navHostControllerWrapper: NavHostControllerWrapper,
    onOpenFirstAccount: suspend () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountImportViewModel = viewModel(factory = AccountImportViewModel.factory(LocalContext.current.applicationContext, LocalAppScope.current)),
) {
    val appScope = LocalAppScope.current
    val amber = LocalContext.current.amber
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val failedLines by viewModel.failedLines.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf(TextFieldValue()) }
    var showImportSheet by remember { mutableStateOf(false) }
    val padding = loginPadding()

    val finishImport: (Boolean) -> Unit = { hadAccounts ->
        appScope.launch {
            if (hadAccounts) {
                withContext(Dispatchers.Main) { navHostControllerWrapper.navController.navigate(Route.Applications.route) { popUpTo(0) } }
            } else {
                onOpenFirstAccount()
            }
            amber.notificationSubscription.updateFilter()
        }
    }
    val selectFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        showImportSheet = false
        uri?.let { viewModel.import(it, password.text, finishImport) }
    }

    if (failedLines.isNotEmpty()) ImportErrorDialog(failedLines, onDismiss = viewModel::dismissImportError)
    if (showImportSheet) {
        ImportSheet(password, onPassword = { password = it }, onPickFile = { selectFileLauncher.launch("*/*") }, onDismiss = { showImportSheet = false })
    }

    Scaffold(modifier) {
        Column(Modifier.fillMaxSize().padding(it).padding(horizontal = padding).padding(top = padding * 1.5f)) {
            if (isLoading) {
                CenterCircularProgressIndicator(modifier = Modifier.fillMaxSize(), text = progress)
            } else {
                MainPageContent(
                    onAddKey = { navHostControllerWrapper.navController.navigate("loginPage") },
                    onGenerateKey = { navHostControllerWrapper.navController.navigate("create") },
                    onRecover = { showImportSheet = true },
                )
            }
        }
    }
}

@Composable
private fun ImportErrorDialog(failedLines: List<Int>, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.recover_from_backup)) },
        text = { Text(stringResource(Res.string.import_failed_for_accounts, failedLines.joinToString(", "))) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    password: TextFieldValue,
    onPassword: (TextFieldValue) -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        sheetState = rememberModalBottomSheetState(confirmValueChange = { it != SheetValue.PartiallyExpanded }, skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
            Column(Modifier.padding(8.dp)) {
                SecretField(
                    value = password,
                    onValueChange = onPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.encryption_password),
                    placeholder = stringResource(Res.string.enter_strong_password),
                    imeAction = ImeAction.Next,
                )
                AmberButton(onClick = onPickFile, text = stringResource(Res.string.recover_from_backup))
            }
        }
    }
}

@Composable
private fun MainPageContent(onAddKey: () -> Unit, onGenerateKey: () -> Unit, onRecover: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = androidStringResource(R.string.app_name_release).toUpperCase(Locale.current), fontSize = 36.sp)
        Text(text = stringResource(Res.string.a_nostr_secure_signer), fontSize = 16.sp, color = Color(0xFFC98500))
        PreviousOfflineInstallNotice(Modifier.padding(top = 12.dp))
        Image(
            modifier = Modifier.padding(top = 20.dp, bottom = 20.dp),
            imageVector = ImageVector.vectorResource(R.drawable.frame),
            contentDescription = "Logo",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmberElevatedButton(
                contentColor = Color(0xFF4C4C4C),
                textColor = MaterialTheme.colorScheme.primary,
                onClick = onAddKey,
                text = stringResource(Res.string.add_a_key),
            )
            AmberButton(onClick = onGenerateKey, text = stringResource(Res.string.generate_a_new_key))
            AmberButton(onClick = onRecover, text = stringResource(Res.string.recover_from_backup))
        }
        Spacer(Modifier.weight(1f))
        SourceCodeFooter()
    }
}

@Composable
private fun SourceCodeFooter() {
    val message = stringResource(Res.string.amber_is_a_free_and_open_source_project)
    val githubUri = stringResource(Res.string.amber_github_uri)
    val checkTheCodeMessage = stringResource(Res.string.check_the_code)
    Text(
        modifier = Modifier.padding(bottom = 20.dp),
        color = Color(0xFF8C8C8C),
        text = buildAnnotatedString {
            withStyle(style = ParagraphStyle(textAlign = TextAlign.Center)) {
                append(message)
                withLink(LinkAnnotation.Url(githubUri, styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)))) {
                    append(checkTheCodeMessage)
                }
            }
        },
    )
}

// The application ids an offline Amber can carry: the one shared with the online
// app (every offline APK released up to 6.6.9) and the one of the separate
// "Amber Offline" Zapstore listing (7.0.0-pre and later).
private val OFFLINE_APP_PACKAGES = listOf(
    "com.greenart7c3.nostrsigner",
    "com.greenart7c3.nostrsigner.offline",
)

/**
 * The package of an installed offline Amber that is not this app, or null.
 *
 * Only the INTERNET permission separates an offline install from the online
 * Amber, which keeps its accounts to itself and needs no notice.
 */
private fun previousOfflineInstall(context: Context): String? {
    if (!BuildFlavorChecker.isOfflineFlavor()) return null
    // This app holds one of those ids too, and pointing at itself explains
    // nothing to a user who is looking at an empty install.
    return OFFLINE_APP_PACKAGES
        .filter { it != context.packageName }
        .firstOrNull { isOfflineInstall(context, it) }
}

@Suppress("DEPRECATION")
private fun isOfflineInstall(context: Context, packageName: String): Boolean {
    val permissions = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
    } catch (_: PackageManager.NameNotFoundException) {
        return false
    }
    return !permissions.orEmpty().contains(Manifest.permission.INTERNET)
}

/**
 * The offline app can end up next to an install that still holds the accounts,
 * when the two use different application ids: an update then arrives as a
 * separate, empty app, because its data cannot be read across packages. Point
 * the user at the app that has the accounts instead of showing them a first run.
 */
@Composable
private fun PreviousOfflineInstallNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val previousApp = remember(context) { previousOfflineInstall(context) } ?: return
    val launchIntent = remember(context) { context.packageManager.getLaunchIntentForPackage(previousApp) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.offline_previous_install_title),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(Res.string.offline_previous_install_body),
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        if (launchIntent != null) {
            TextButton(
                onClick = { context.startActivity(launchIntent) },
            ) {
                Text(
                    text = stringResource(Res.string.offline_previous_install_open),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun MainLoginPage(
    accountViewModel: AccountStateViewModel,
    navHostControllerWrapper: NavHostControllerWrapper,
) {
    val appScope = LocalAppScope.current
    val amber = LocalContext.current.amber
    NavHost(navHostControllerWrapper.navController, startDestination = "login") {
        composable("login") {
            MainPage(navHostControllerWrapper, onOpenFirstAccount = accountViewModel::openFirstImportedAccount)
        }
        composable("create") {
            SignUpPage(
                navHostControllerWrapper = navHostControllerWrapper,
                onCreate = { setup, seedWords, name -> accountViewModel.newKey(setup.torMode, setup.proxyPort, setup.signPolicy, seedWords, name) },
                onFinish = { appScope.launch { amber.notificationSubscription.updateFilter() } },
            )
        }
        composable("loginPage") {
            LoginPage(
                navHostControllerWrapper = navHostControllerWrapper,
                onLogin = { keyPair, setup -> accountViewModel.startUI(keyPair, null, setup.torMode, setup.proxyPort, setup.signPolicy) },
                onFinish = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginTopBar(title: String) {
    CenterAlignedTopAppBar(title = { Text(text = title) })
}

// Back steps through the pager first, then leaves the page.
@Composable
private fun LoginBottomBar(pagerState: PagerState, navHostControllerWrapper: NavHostControllerWrapper) {
    val scope = rememberCoroutineScope()
    BottomAppBar {
        IconRow(
            center = true,
            title = stringResource(Res.string.go_back),
            icon = ImageVector.vectorResource(R.drawable.back),
            onClick = {
                if (pagerState.currentPage > 0) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                } else {
                    navHostControllerWrapper.navController.navigateUp()
                }
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SignUpPage(
    navHostControllerWrapper: NavHostControllerWrapper,
    onCreate: suspend (ConnectionSetup, Set<String>, String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = viewModel(factory = SignUpViewModel.factory(LocalAppScope.current)),
) {
    val newKey by viewModel.newKey.collectAsStateWithLifecycle()
    var nickname by remember { mutableStateOf(TextFieldValue()) }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val padding = loginPadding()

    Scaffold(
        modifier = modifier,
        topBar = { LoginTopBar(stringResource(if (pagerState.currentPage == 0) Res.string.generate_a_new_key else Res.string.permissions_and_connection)) },
        bottomBar = { LoginBottomBar(pagerState, navHostControllerWrapper) },
    ) { contentPadding ->
        val key = newKey
        if (key == null) {
            CenterCircularProgressIndicator(Modifier.fillMaxSize(), stringResource(Res.string.do_not_leave_the_app_until_the_key_is_generated))
        } else {
            HorizontalPager(modifier = Modifier.fillMaxSize(), state = pagerState, userScrollEnabled = false) { page ->
                when (page) {
                    0 -> NewAccountForm(
                        npub = key.keyPair.pubKey.toNpub(),
                        nickname = nickname,
                        onNickname = { nickname = it },
                        onContinue = { scope.launch { pagerState.animateScrollToPage(1) } },
                        contentPadding = contentPadding,
                        horizontalPadding = padding,
                    )
                    else -> ConnectionSetupPage(contentPadding, padding, onFinish = { viewModel.create(it, nickname.text, onCreate, onFinish) })
                }
            }
        }
    }
}

@Composable
private fun NewAccountForm(
    npub: String,
    nickname: TextFieldValue,
    onNickname: (TextFieldValue) -> Unit,
    onContinue: () -> Unit,
    contentPadding: PaddingValues,
    horizontalPadding: Dp,
) {
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = horizontalPadding)
            .padding(top = horizontalPadding * 1.5f)
            .imePadding(),
    ) {
        Text(text = stringResource(Res.string.your_nostr_account_is_ready), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = stringResource(Res.string.your_nostr_account_explainer), modifier = Modifier.padding(bottom = 8.dp))
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFDE9E))) {
            Text(text = npub, modifier = Modifier.padding(10.dp), color = Color.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.you_will_find_it_in_your_account_section_so_you_don_t_need_to_copy_it_right_now))
        Text(
            text = stringResource(Res.string.please_add_a_nickname_later_you_will_able_to_personalise_your_full_profile_bio_pic_etc_using_your_preferred_nostr_client),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = onNickname,
            placeholder = { Text(stringResource(Res.string.nickname), color = TextFieldDefaults.colors().unfocusedPlaceholderColor) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            modifier = Modifier.fillMaxWidth(),
        )
        AmberButton(
            enabled = nickname.text.isNotBlank(),
            modifier = Modifier.padding(vertical = 40.dp),
            onClick = {
                keyboardController?.hide()
                onContinue()
            },
            text = stringResource(Res.string.continue_button),
        )
    }
}

/** What the user typed on the "add a key" page; saved across process death except the phrase words. */
@Stable
private class AddKeyInput(key: String = "", password: String = "", isMnemonicMode: Boolean = false, wordCount: Int = 12, accountIndex: Int = 0) {
    var key by mutableStateOf(TextFieldValue(key))
    var password by mutableStateOf(TextFieldValue(password))
    var isMnemonicMode by mutableStateOf(isMnemonicMode)
    var wordCount by mutableIntStateOf(wordCount)
    var words by mutableStateOf(List(wordCount) { "" })
    var accountIndex by mutableIntStateOf(accountIndex)

    val needsPassword: Boolean get() = key.text.startsWith("ncryptsec1")

    val canSubmit: Boolean
        get() = if (isMnemonicMode) words.all { it.isNotBlank() } else key.text.isNotBlank() && !(needsPassword && password.text.isBlank())

    companion object {
        val Saver = listSaver<AddKeyInput, Any>(
            save = { listOf(it.key.text, it.password.text, it.isMnemonicMode, it.wordCount, it.accountIndex) },
            restore = { AddKeyInput(it[0] as String, it[1] as String, it[2] as Boolean, it[3] as Int, it[4] as Int) },
        )
    }
}

@Composable
fun LoginPage(
    navHostControllerWrapper: NavHostControllerWrapper,
    onLogin: suspend (KeyPair, ConnectionSetup) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddKeyViewModel = viewModel(factory = AddKeyViewModel.factory(LocalAppScope.current)),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val input = rememberSaveable(saver = AddKeyInput.Saver) { AddKeyInput() }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val padding = loginPadding()
    val keyRequired = stringResource(Res.string.key_is_required)
    val passwordRequired = stringResource(Res.string.password_is_required)

    val goToSetup = {
        keyboardController?.hide()
        scope.launch {
            delay(200.milliseconds)
            pagerState.animateScrollToPage(1)
        }
        Unit
    }
    val submit = {
        when {
            input.isMnemonicMode -> viewModel.submitMnemonic(input.words, input.accountIndex, goToSetup)
            input.needsPassword && input.password.text.isBlank() -> viewModel.showError(passwordRequired)
            input.key.text.isBlank() -> viewModel.showError(keyRequired)
            else -> viewModel.submitKey(input.key.text, input.password.text, goToSetup)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { LoginTopBar(stringResource(if (pagerState.currentPage == 0) Res.string.add_a_key else Res.string.permissions_and_connection)) },
        bottomBar = { LoginBottomBar(pagerState, navHostControllerWrapper) },
    ) { contentPadding ->
        HorizontalPager(pagerState, userScrollEnabled = false) { page ->
            when {
                isLoading -> CenterCircularProgressIndicator(Modifier, stringResource(Res.string.do_not_leave_the_app_until_the_key_is_generated))
                page == 0 -> AddKeyForm(input, error, onClearError = { viewModel.showError("") }, onSubmit = submit, contentPadding, padding)
                else -> ConnectionSetupPage(contentPadding, padding, onFinish = { viewModel.login(it, onLogin, onFinish) })
            }
        }
    }
}

@Composable
private fun AddKeyForm(
    input: AddKeyInput,
    error: String,
    onClearError: () -> Unit,
    onSubmit: () -> Unit,
    contentPadding: PaddingValues,
    horizontalPadding: Dp,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = horizontalPadding)
            .padding(top = horizontalPadding * 1.5f)
            .imePadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !input.isMnemonicMode,
                onClick = {
                    input.isMnemonicMode = false
                    onClearError()
                },
                label = { Text(stringResource(Res.string.login_mode_private_key)) },
            )
            FilterChip(
                selected = input.isMnemonicMode,
                onClick = {
                    input.isMnemonicMode = true
                    onClearError()
                },
                label = { Text(stringResource(Res.string.login_mode_recovery_phrase)) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (input.isMnemonicMode) MnemonicSection(input, onClearError) else PrivateKeySection(input, onClearError, onSubmit)
        if (error.isNotBlank()) Text(text = error, color = MaterialTheme.colorScheme.error)
        AmberButton(enabled = input.canSubmit, onClick = onSubmit, text = stringResource(Res.string.next))
    }
}

@Composable
private fun MnemonicSection(input: AddKeyInput, onClearError: () -> Unit) {
    Column {
        val scope = rememberCoroutineScope()
        val clipboardManager = LocalClipboard.current
        Text(stringResource(Res.string.enter_recovery_phrase_description))
        Spacer(modifier = Modifier.height(12.dp))
        MnemonicLoginInput(
            wordCount = input.wordCount,
            words = input.words,
            onWordChange = { index, word -> input.words = input.words.toMutableList().also { it[index] = word } },
            onWordCountChange = { count ->
                input.wordCount = count
                input.words = List(count) { "" }
                onClearError()
            },
            onPaste = {
                scope.launch {
                    val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString() ?: ""
                    if (text.isNotBlank()) {
                        val pastedWords = text.trim().split(Regex("\\s+"))
                        val count = if (pastedWords.size > 12) 24 else 12
                        input.wordCount = count
                        input.words = List(count) { i -> pastedWords.getOrElse(i) { "" }.toLowerCase(Locale.current) }
                        onClearError()
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            value = if (input.accountIndex == 0) "" else input.accountIndex.toString(),
            onValueChange = { value ->
                input.accountIndex = value.filter(Char::isDigit).toIntOrNull() ?: 0
                onClearError()
            },
            label = { Text(stringResource(Res.string.mnemonic_account_index)) },
            placeholder = { Text("0") },
            supportingText = { Text(stringResource(Res.string.mnemonic_account_index_description, input.accountIndex)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            singleLine = true,
        )
    }
}

@Composable
private fun PrivateKeySection(input: AddKeyInput, onClearError: () -> Unit, onSubmit: () -> Unit) {
    Column {
        val focusRequester = remember { FocusRequester() }
        var scannerOpen by rememberSaveable { mutableStateOf(false) }
        Text(stringResource(Res.string.setup_amber_with_your_nostr_private_key_you_can_enter_different_versions_nsec_ncryptsec_or_hex_you_can_also_scan_it_from_a_qr_code))
        SecretField(
            value = input.key,
            onValueChange = {
                input.key = it
                onClearError()
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).padding(vertical = 20.dp),
            placeholder = stringResource(Res.string.nsec),
            shape = RoundedCornerShape(18.dp),
            onGo = onSubmit,
            leadingIcon = {
                if (scannerOpen) {
                    SimpleQrCodeScanner(onDismiss = { scannerOpen = false }) { content ->
                        scannerOpen = false
                        if (!content.isNullOrEmpty()) input.key = TextFieldValue(content.toLowerCase(Locale.current))
                    }
                }
                IconButton(onClick = { scannerOpen = true }) {
                    Icon(painter = painterResource(R.drawable.ic_qrcode), null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
            },
        )
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            focusRequester.requestFocus()
        }
        if (input.needsPassword) {
            SecretField(
                value = input.password,
                onValueChange = {
                    input.password = it
                    onClearError()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                placeholder = stringResource(Res.string.ncryptsec_password),
                shape = RoundedCornerShape(18.dp),
                onGo = onSubmit,
            )
        }
    }
}

/** A password-style field with a show/hide toggle. */
@Composable
private fun SecretField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    imeAction: ImeAction = ImeAction.Go,
    onGo: () -> Unit = {},
    shape: Shape = OutlinedTextFieldDefaults.shape,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = modifier.semantics { contentType = ContentType.Password },
        shape = shape,
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        label = label?.let { { Text(text = it) } },
        placeholder = { Text(text = placeholder) },
        leadingIcon = leadingIcon,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) Res.string.hide_password else Res.string.show_password),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    )
}
