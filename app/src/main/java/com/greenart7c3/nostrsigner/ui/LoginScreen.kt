package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.AccountExportService
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberElevatedButton
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.RichTextDefaults
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MainPage(
    scope: CoroutineScope,
    navController: NavController,
    accountViewModel: AccountStateViewModel,
) {
    var isLoading by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(TextFieldValue()) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val percentage = (screenWidthDp * 0.93f)
    val verticalPadding = (screenWidthDp - percentage)
    val context = LocalContext.current
    var shouldShowBottomSheet by remember { mutableStateOf(false) }
    val selectFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        shouldShowBottomSheet = false
        Amber.instance.applicationIOScope.launch {
            uri?.let {
                val hasAccounts = LocalPreferences.allAccounts(Amber.instance).isNotEmpty()
                AccountExportService.importAccounts(
                    uri = uri,
                    password = password.text,
                    onText = {
                        text = it
                    },
                    onLoading = {
                        isLoading = it
                    },
                    onFinish = {
                        Amber.instance.applicationIOScope.launch {
                            if (hasAccounts) {
                                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                    navController.navigate(Route.Applications.route) {
                                        popUpTo(0)
                                    }
                                }
                            } else {
                                val accounts = LocalPreferences.allAccounts(Amber.instance)
                                if (accounts.isNotEmpty()) {
                                    val account = LocalPreferences.loadFromEncryptedStorage(Amber.instance, accounts.first().npub)
                                    account?.let {
                                        accountViewModel.startUI(account, null)
                                    }
                                }
                            }
                            Amber.instance.notificationSubscription.updateFilter()
                            Amber.instance.profileSubscription.updateFilter()
                        }
                    },
                )
            }
        }
    }

    val sheetState =
        rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
            skipPartiallyExpanded = true,
        )

    if (shouldShowBottomSheet) {
        var showCharsPassword by remember { mutableStateOf(false) }
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                shouldShowBottomSheet = false
            },
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    1f,
                ),
            ) {
                Column(
                    Modifier.padding(8.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentType = ContentType.Password
                            },
                        value = password,
                        onValueChange = {
                            password = it
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        label = {
                            Text(text = stringResource(R.string.encryption_password))
                        },
                        placeholder = {
                            Text(text = stringResource(R.string.enter_strong_password))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                                Icon(
                                    imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showCharsPassword) {
                                        stringResource(R.string.hide_password)
                                    } else {
                                        stringResource(R.string.show_password)
                                    },
                                )
                            }
                        },
                        visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    AmberButton(
                        onClick = {
                            selectFileLauncher.launch("*/*")
                        },
                        text = stringResource(R.string.recover_from_backup),
                    )
                }
            }
        }
    }

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = verticalPadding)
                .padding(top = verticalPadding * 1.5f),
        ) {
            if (isLoading) {
                CenterCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    text = text,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.app_name_release).toUpperCase(Locale.current),
                        fontSize = 36.sp,
                    )
                    Text(
                        text = stringResource(R.string.a_nostr_secure_signer),
                        fontSize = 16.sp,
                        color = Color(0xFFC98500),
                    )

                    Image(
                        modifier = Modifier.padding(top = 20.dp, bottom = 20.dp),
                        imageVector = ImageVector.vectorResource(R.drawable.frame),
                        contentDescription = "Logo",
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AmberElevatedButton(
                            contentColor = Color(0xFF4C4C4C),
                            textColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                scope.launch {
                                    navController.navigate("loginPage")
                                }
                            },
                            text = stringResource(R.string.add_a_key),
                        )

                        AmberButton(
                            onClick = {
                                scope.launch {
                                    navController.navigate("create")
                                }
                            },
                            text = stringResource(R.string.generate_a_new_key),
                        )

                        AmberButton(
                            onClick = {
                                shouldShowBottomSheet = true
                            },
                            text = stringResource(R.string.recover_from_backup),
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        modifier = Modifier.padding(bottom = 20.dp),
                        color = Color(0xFF8C8C8C),
                        text = buildAnnotatedString {
                            withStyle(
                                style = ParagraphStyle(
                                    textAlign = TextAlign.Center,
                                ),
                            ) {
                                append("Amber is a free and open source project\n")
                                withLink(
                                    LinkAnnotation.Url(
                                        context.getString(R.string.amber_github_uri),
                                        styles = TextLinkStyles(
                                            style = SpanStyle(
                                                textDecoration = TextDecoration.Underline,
                                            ),
                                        ),
                                    ),
                                ) {
                                    append(context.getString(R.string.check_the_code))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun MainLoginPage(
    accountViewModel: AccountStateViewModel,
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()

    NavHost(
        navController,
        startDestination = "login",
    ) {
        composable(
            "login",
            content = {
                MainPage(
                    scope = scope,
                    navController = navController,
                    accountViewModel = accountViewModel,
                )
            },
        )

        composable(
            "create",
            content = {
                SignUpPage(
                    accountViewModel = accountViewModel,
                    scope = scope,
                    navController = navController,
                    onFinish = {
                        Amber.instance.applicationIOScope.launch {
                            Amber.instance.profileSubscription.updateFilter()
                            Amber.instance.notificationSubscription.updateFilter()
                        }
                    },
                )
            },
        )

        composable(
            "loginPage",
            content = {
                LoginPage(
                    accountViewModel = accountViewModel,
                    navController = navController,
                    onFinish = {},
                )
            },
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpPage(
    accountViewModel: AccountStateViewModel,
    scope: CoroutineScope,
    navController: NavController,
    onFinish: () -> Unit,
) {
    var loading by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val percentage = (screenWidthDp * 0.93f)
    val verticalPadding = (screenWidthDp - percentage)
    var nickname by remember { mutableStateOf(TextFieldValue()) }
    var keyPair by remember { mutableStateOf(KeyPair()) }
    val state = rememberPagerState {
        2
    }
    val context = LocalContext.current
    var seedWords by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            loading = true
            while (seedWords.size < 12) {
                val entropy = RandomInstance.bytes(16)
                seedWords = Bip39Mnemonics.toMnemonics(entropy).toSet()
                keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(seedWords.joinToString(" ")))
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when (state.currentPage) {
                        0 -> {
                            Text(text = stringResource(R.string.generate_a_new_key))
                        }
                        else -> {
                            Text(text = stringResource(R.string.permissions_and_connection))
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconRow(
                    center = true,
                    title = stringResource(R.string.go_back),
                    icon = ImageVector.vectorResource(R.drawable.back),
                    onClick = {
                        if (state.currentPage > 0) {
                            scope.launch {
                                state.animateScrollToPage(state.currentPage - 1)
                            }
                        } else {
                            scope.launch {
                                navController.navigateUp()
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (loading) {
            CenterCircularProgressIndicator(
                Modifier.fillMaxSize(),
                stringResource(R.string.do_not_leave_the_app_until_the_key_is_generated),
            )
        } else {
            HorizontalPager(
                modifier = Modifier
                    .fillMaxSize(),
                state = state,
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> {
                        val scrollState = rememberScrollState()
                        val keyboardController = LocalSoftwareKeyboardController.current
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(it)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f)
                                .imePadding(),
                        ) {
                            Text(
                                text = stringResource(R.string.your_nostr_account_is_ready),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = stringResource(R.string.your_nostr_account_explainer),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFDE9E)),
                            ) {
                                Text(
                                    text = keyPair.pubKey.toNpub(),
                                    modifier = Modifier.padding(10.dp),
                                    color = Color.Black,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.you_will_find_it_in_your_account_section_so_you_don_t_need_to_copy_it_right_now))

                            Text(
                                text = stringResource(R.string.please_add_a_nickname_later_you_will_able_to_personalise_your_full_profile_bio_pic_etc_using_your_preferred_nostr_client),
                                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                            )
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { value ->
                                    nickname = value
                                },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.nickname),
                                        color = TextFieldDefaults.colors().unfocusedPlaceholderColor,
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            AmberButton(
                                enabled = nickname.text.isNotBlank(),
                                modifier = Modifier.padding(vertical = 40.dp),
                                onClick = {
                                    if (nickname.text.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Nickname is required",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@AmberButton
                                    }
                                    keyboardController?.hide()
                                    scope.launch {
                                        state.animateScrollToPage(1)
                                    }
                                },
                                text = stringResource(R.string.continue_button),
                            )
                        }
                    }

                    1 -> {
                        val scrollState = rememberScrollState()
                        val radioOptions = listOf(
                            TitleExplainer(
                                title = stringResource(R.string.sign_policy_basic),
                                explainer = stringResource(R.string.sign_policy_basic_explainer),
                            ),
                            TitleExplainer(
                                title = stringResource(R.string.sign_policy_manual),
                                explainer = stringResource(R.string.sign_policy_manual_explainer),
                            ),
                        )
                        var selectedOption by remember { mutableIntStateOf(0) }
                        var useProxy by remember { mutableStateOf(false) }
                        var proxyPort by remember { mutableStateOf(TextFieldValue("9050")) }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(it)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        ) {
                            Text(
                                text = stringResource(R.string.sign_policy_explainer),
                            )
                            Column(
                                Modifier.padding(vertical = 10.dp),
                            ) {
                                radioOptions.forEachIndexed { index, option ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedOption == index,
                                                onClick = {
                                                    selectedOption = index
                                                },
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selectedOption == index) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selectedOption == index,
                                            onClick = {
                                                selectedOption = index
                                            },
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = option.title,
                                                modifier = Modifier.padding(start = 16.dp),
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            option.explainer?.let { explainer ->
                                                Text(
                                                    text = explainer,
                                                    modifier = Modifier.padding(start = 16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (LocalPreferences.allSavedAccounts(context).isEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(vertical = 20.dp)
                                        .clickable {
                                            useProxy = !useProxy
                                        },
                                ) {
                                    Switch(
                                        modifier = Modifier.scale(0.85f),
                                        checked = useProxy,
                                        onCheckedChange = { value ->
                                            useProxy = value
                                        },
                                    )
                                    Text(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                        text = stringResource(R.string.connect_through_your_orbot_setup),
                                    )
                                }

                                if (useProxy) {
                                    val myMarkDownStyle =
                                        RichTextDefaults.copy(
                                            stringStyle = RichTextDefaults.stringStyle?.copy(
                                                linkStyle = TextLinkStyles(
                                                    SpanStyle(
                                                        textDecoration = TextDecoration.Underline,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    ),
                                                ),
                                            ),
                                        )
                                    val content1 = stringResource(R.string.connect_through_your_orbot_setup_markdown2)

                                    val astNode1 =
                                        remember {
                                            CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content1)
                                        }

                                    RichText(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        style = myMarkDownStyle,
                                        renderer = null,
                                    ) {
                                        BasicMarkdown(astNode1)
                                    }

                                    OutlinedTextField(
                                        value = proxyPort,
                                        onValueChange = { value ->
                                            proxyPort = value
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(R.string.orbot_socks_port),
                                                color = TextFieldDefaults.colors().unfocusedPlaceholderColor,
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.orbot_socks_port),
                                                color = TextFieldDefaults.colors().unfocusedPlaceholderColor,
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.None,
                                            autoCorrectEnabled = false,
                                            imeAction = ImeAction.Next,
                                            keyboardType = KeyboardType.Number,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 20.dp),
                                    )
                                }
                            }
                            AmberButton(
                                modifier = Modifier
                                    .padding(vertical = 20.dp),
                                onClick = {
                                    if (proxyPort.text.toIntOrNull() == null) {
                                        Toast.makeText(
                                            context,
                                            "Invalid port number",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@AmberButton
                                    }

                                    Amber.instance.applicationIOScope.launch {
                                        loading = true
                                        accountViewModel.newKey(
                                            useProxy = useProxy,
                                            signPolicy = selectedOption,
                                            proxyPort = proxyPort.text.toInt(),
                                            seedWords = seedWords,
                                            name = nickname.text,
                                        )
                                        loading = false
                                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                            onFinish()
                                        }
                                    }
                                },
                                text = stringResource(R.string.finish),
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginPage(
    accountViewModel: AccountStateViewModel,
    navController: NavController,
    onFinish: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val percentage = (screenWidthDp * 0.93f)
    val verticalPadding = (screenWidthDp - percentage)
    val key = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    val needsPassword =
        remember {
            derivedStateOf {
                key.value.text.startsWith("ncryptsec1")
            }
        }
    val password = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val context = LocalContext.current
    val pageState = rememberPagerState {
        2
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var keyPair by remember { mutableStateOf(KeyPair()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (pageState.currentPage == 0) {
                        Text(text = stringResource(R.string.add_a_key))
                    } else {
                        Text(text = stringResource(R.string.permissions_and_connection))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconRow(
                    center = true,
                    title = stringResource(R.string.go_back),
                    icon = ImageVector.vectorResource(R.drawable.back),
                    onClick = {
                        if (pageState.currentPage > 0) {
                            scope.launch {
                                pageState.animateScrollToPage(pageState.currentPage - 1)
                            }
                        } else {
                            scope.launch {
                                navController.navigateUp()
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        HorizontalPager(
            pageState,
            userScrollEnabled = false,
        ) { page ->
            if (isLoading) {
                CenterCircularProgressIndicator(
                    Modifier,
                    stringResource(R.string.do_not_leave_the_app_until_the_key_is_generated),
                )
            } else {
                when (page) {
                    0 -> {
                        val scrollState = rememberScrollState()
                        val keyboardController = LocalSoftwareKeyboardController.current

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(it)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f)
                                .imePadding(),
                        ) {
                            var showPassword by remember {
                                mutableStateOf(false)
                            }

                            var showCharsPassword by remember { mutableStateOf(false) }

                            Text(
                                stringResource(R.string.setup_amber_with_your_nostr_private_key_you_can_enter_different_versions_nsec_ncryptsec_or_hex_you_can_also_scan_it_from_a_qr_code),
                            )

                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentType = ContentType.Password
                                    }
                                    .focusRequester(focusRequester)
                                    .padding(vertical = 20.dp),
                                shape = RoundedCornerShape(18.dp),
                                value = key.value,
                                onValueChange = { value ->
                                    key.value = value
                                    if (errorMessage.isNotEmpty()) {
                                        errorMessage = ""
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Go,
                                ),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.nsec),
                                    )
                                },
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = if (showPassword) {
                                                    stringResource(R.string.show_password)
                                                } else {
                                                    stringResource(R.string.hide_password)
                                                },
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    if (dialogOpen) {
                                        SimpleQrCodeScanner { content ->
                                            dialogOpen = false
                                            if (!content.isNullOrEmpty()) {
                                                key.value = TextFieldValue(content.toLowerCase(Locale.current))
                                            }
                                        }
                                    }
                                    IconButton(onClick = { dialogOpen = true }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_qrcode),
                                            null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (key.value.text.isBlank()) {
                                            errorMessage = context.getString(R.string.key_is_required)
                                        }

                                        if (needsPassword.value && password.value.text.isBlank()) {
                                            errorMessage = context.getString(R.string.password_is_required)
                                        }

                                        if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                                            Amber.instance.applicationIOScope.launch {
                                                isLoading = true
                                                try {
                                                    val isValid = accountViewModel.isValidKey(key.value.text.filter { value -> value.code in 33..126 || value.code == 32 }.toLowerCase(Locale.current).trim(), password.value.text)
                                                    isLoading = false
                                                    if (isValid.first != null) {
                                                        keyPair = isValid.first!!
                                                        scope.launch {
                                                            delay(200)
                                                            pageState.animateScrollToPage(1)
                                                        }
                                                    } else {
                                                        errorMessage = isValid.second
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = e.message.toString()
                                                    isLoading = false
                                                }
                                            }

                                        }
                                    },
                                ),
                            )

                            LaunchedEffect(Unit) {
                                withFrameNanos { }
                                withFrameNanos { }
                                focusRequester.requestFocus()
                            }

                            if (needsPassword.value) {
                                OutlinedTextField(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentType = ContentType.Password
                                        }
                                        .padding(bottom = 20.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    value = password.value,
                                    onValueChange = { value ->
                                        password.value = value
                                        if (errorMessage.isNotEmpty()) {
                                            errorMessage = ""
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Go,
                                    ),
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.ncryptsec_password),
                                        )
                                    },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                                                Icon(
                                                    imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                    contentDescription = if (showCharsPassword) {
                                                        stringResource(R.string.show_password)
                                                    } else {
                                                        stringResource(
                                                            R.string.hide_password,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    },
                                    visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            if (key.value.text.isBlank()) {
                                                errorMessage = context.getString(R.string.key_is_required)
                                            }

                                            if (needsPassword.value && password.value.text.isBlank()) {
                                                errorMessage = context.getString(R.string.password_is_required)
                                            }

                                            if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                                                Amber.instance.applicationIOScope.launch {
                                                    isLoading = true
                                                    try {
                                                        val isValid = accountViewModel.isValidKey(key.value.text.filter { value -> value.code in 33..126 || value.code == 32 }.toLowerCase(Locale.current).trim(), password.value.text)
                                                        isLoading = false
                                                        if (isValid.first != null) {
                                                            keyPair = isValid.first!!
                                                            scope.launch {
                                                                delay(200)
                                                                pageState.animateScrollToPage(1)
                                                            }
                                                        } else {
                                                            errorMessage = isValid.second
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage = e.message.toString()
                                                        isLoading = false
                                                    }
                                                }

                                            }
                                        },
                                    ),
                                )
                            }

                            if (errorMessage.isNotBlank()) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            AmberButton(
                                enabled = key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank()),
                                onClick = {
                                    if (key.value.text.isBlank()) {
                                        errorMessage = context.getString(R.string.key_is_required)
                                    }

                                    if (needsPassword.value && password.value.text.isBlank()) {
                                        errorMessage = context.getString(R.string.password_is_required)
                                    }

                                    if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                                        Amber.instance.applicationIOScope.launch {
                                            isLoading = true
                                            try {
                                                val isValid = accountViewModel.isValidKey(key.value.text.filter { value -> value.code in 33..126 || value.code == 32 }.toLowerCase(Locale.current).trim(), password.value.text)
                                                isLoading = false
                                                if (isValid.first != null) {
                                                    keyPair = isValid.first!!
                                                    keyboardController?.hide()
                                                    scope.launch {
                                                        delay(200)
                                                        pageState.animateScrollToPage(1)
                                                    }
                                                } else {
                                                    errorMessage = isValid.second
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = e.message.toString()
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                text = stringResource(R.string.next),
                            )
                        }
                    }

                    1 -> {
                        val radioOptions = listOf(
                            TitleExplainer(
                                title = stringResource(R.string.sign_policy_basic),
                                explainer = stringResource(R.string.sign_policy_basic_explainer),
                            ),
                            TitleExplainer(
                                title = stringResource(R.string.sign_policy_manual),
                                explainer = stringResource(R.string.sign_policy_manual_explainer),
                            ),
                        )
                        var selectedOption by remember { mutableIntStateOf(0) }
                        var useProxy by remember { mutableStateOf(false) }
                        var proxyPort by remember { mutableStateOf(TextFieldValue("9050")) }
                        val scrollState = rememberScrollState()

                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(it)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        ) {
                            Text(
                                text = stringResource(R.string.sign_policy_explainer),
                            )
                            Column(
                                Modifier.padding(vertical = 10.dp),
                            ) {
                                radioOptions.forEachIndexed { index, option ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedOption == index,
                                                onClick = {
                                                    selectedOption = index
                                                },
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selectedOption == index) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selectedOption == index,
                                            onClick = {
                                                selectedOption = index
                                            },
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = option.title,
                                                modifier = Modifier.padding(start = 16.dp),
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            option.explainer?.let { explainer ->
                                                Text(
                                                    text = explainer,
                                                    modifier = Modifier.padding(start = 16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (LocalPreferences.allSavedAccounts(context).isEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(vertical = 20.dp)
                                        .clickable {
                                            useProxy = !useProxy
                                        },
                                ) {
                                    Switch(
                                        modifier = Modifier.scale(0.85f),
                                        checked = useProxy,
                                        onCheckedChange = { value ->
                                            useProxy = value
                                        },
                                    )
                                    Text(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                        text = stringResource(R.string.connect_through_your_orbot_setup),
                                    )
                                }

                                if (useProxy) {
                                    val myMarkDownStyle =
                                        RichTextDefaults.copy(
                                            stringStyle = RichTextDefaults.stringStyle?.copy(
                                                linkStyle = TextLinkStyles(
                                                    SpanStyle(
                                                        textDecoration = TextDecoration.Underline,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    ),
                                                ),
                                            ),
                                        )
                                    val content1 = stringResource(R.string.connect_through_your_orbot_setup_markdown2)

                                    val astNode1 =
                                        remember {
                                            CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content1)
                                        }

                                    RichText(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        style = myMarkDownStyle,
                                        renderer = null,
                                    ) {
                                        BasicMarkdown(astNode1)
                                    }

                                    OutlinedTextField(
                                        value = proxyPort,
                                        onValueChange = { value ->
                                            proxyPort = value
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(R.string.orbot_socks_port),
                                                color = TextFieldDefaults.colors().unfocusedPlaceholderColor,
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.orbot_socks_port),
                                                color = TextFieldDefaults.colors().unfocusedPlaceholderColor,
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.None,
                                            autoCorrectEnabled = false,
                                            imeAction = ImeAction.Next,
                                            keyboardType = KeyboardType.Number,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 20.dp),
                                    )
                                }
                            }
                            AmberButton(
                                modifier = Modifier
                                    .padding(vertical = 20.dp),
                                onClick = {
                                    if (proxyPort.text.toIntOrNull() == null) {
                                        Toast.makeText(
                                            context,
                                            "Invalid port number",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@AmberButton
                                    }

                                    Amber.instance.applicationIOScope.launch {
                                        isLoading = true
                                        accountViewModel.startUI(
                                            keyPair = keyPair,
                                            route = null,
                                            useProxy = useProxy,
                                            signPolicy = selectedOption,
                                            proxyPort = proxyPort.text.toInt(),
                                        )
                                        isLoading = false
                                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                            onFinish()
                                        }
                                    }
                                },
                                text = stringResource(R.string.finish),
                            )
                        }
                    }
                }
            }
        }
    }
}
