package com.greenart7c3.nostrsigner.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.PackageUtils
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotDialog
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.vitorpamplona.quartz.crypto.CryptoUtils.random
import com.vitorpamplona.quartz.crypto.nip06.Bip39Mnemonics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AmberLogo(size: Int) {
    Image(
        painterResource(id = R.mipmap.ic_launcher_foreground),
        contentDescription = "Amber",
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colorResource(id = R.color.amber)),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPage(
    scope: CoroutineScope,
    state: PagerState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(0.dp))

        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmberLogo(200)

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedButton(
                    onClick = {
                        scope.launch {
                            state.animateScrollToPage(1)
                        }
                    },

                    modifier = Modifier
                        .height(50.dp),
                ) {
                    Text(text = stringResource(R.string.add_a_key))
                }

                Button(
                    modifier = Modifier
                        .height(50.dp),
                    onClick = {
                        scope.launch {
                            state.animateScrollToPage(2)
                        }
                    },
                ) {
                    Text(stringResource(R.string.generate_a_new_key))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainLoginPage(
    accountViewModel: AccountStateViewModel,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPagerState {
        3
    }

    HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = state,
        userScrollEnabled = false,
    ) {
        when (it) {
            0 -> {
                MainPage(
                    scope = scope,
                    state = state,
                )
            }
            1 -> {
                LoginPage(
                    accountViewModel = accountViewModel,
                )
            }
            2 -> {
                SignUpPage(
                    accountViewModel = accountViewModel,
                    scope = scope,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignUpPage(
    accountViewModel: AccountStateViewModel,
    scope: CoroutineScope,
) {
    val useProxy = remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf("9050") }
    val connectOrbotDialogOpen = remember { mutableStateOf(false) }
    var seedWords by remember { mutableStateOf(setOf<String>()) }
    val pageState = rememberPagerState {
        3
    }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val entropy = random(16)
            seedWords = Bip39Mnemonics.toMnemonics(entropy).toSet()
            Log.d("NostrSigner", "Seed words: $seedWords")
        }
    }

    HorizontalPager(
        state = pageState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { currentPage ->
        when (currentPage) {
            0 -> {
                SeedWordsPage(
                    seedWords = seedWords,
                    pageState = pageState,
                    scope = scope,
                    context = context,
                )
            }
            1 -> {
                OrbotPage(
                    connectOrbotDialogOpen = connectOrbotDialogOpen,
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    pageState = pageState,
                    scope = scope,
                    context = context,
                )
            }
            else -> {
                SignPolicyScreen(
                    accountViewModel = accountViewModel,
                    key = "",
                    password = "",
                    useProxy = useProxy.value,
                    proxyPort = proxyPort.value.toInt(),
                    seedWords = seedWords,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrbotPage(
    connectOrbotDialogOpen: MutableState<Boolean>,
    useProxy: MutableState<Boolean>,
    proxyPort: MutableState<String>,
    pageState: PagerState,
    scope: CoroutineScope,
    context: Context,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AmberLogo(size = 150)

        Spacer(modifier = Modifier.height(40.dp))

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline" && PackageUtils.isOrbotInstalled(context)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        if (!useProxy.value) {
                            connectOrbotDialogOpen.value = true
                        } else {
                            useProxy.value = false
                        }
                    },
            ) {
                Checkbox(
                    checked = useProxy.value,
                    onCheckedChange = { value ->
                        if (value) {
                            connectOrbotDialogOpen.value = true
                        } else {
                            useProxy.value = false
                        }
                    },
                )

                Text(stringResource(R.string.connect_through_your_orbot_setup))
            }

            if (connectOrbotDialogOpen.value) {
                ConnectOrbotDialog(
                    onClose = { connectOrbotDialogOpen.value = false },
                    onPost = {
                        connectOrbotDialogOpen.value = false
                        useProxy.value = true
                    },
                    onError = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                it,
                                Toast.LENGTH_LONG,
                            )
                                .show()
                        }
                    },
                    proxyPort,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    modifier = Modifier
                        .height(50.dp),
                    onClick = {
                        scope.launch {
                            pageState.animateScrollToPage(2)
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.next))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeedWordsPage(
    seedWords: Set<String>,
    pageState: PagerState,
    scope: CoroutineScope,
    context: Context,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.seed_words_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.seed_words_explainer),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            val firstColumnWords = seedWords.mapIndexedNotNull {
                    index, word ->
                if (index <= 5) {
                    word
                } else {
                    null
                }
            }
            val secondColumnWords = seedWords.mapIndexedNotNull {
                    index, word ->
                if (index > 5) {
                    word
                } else {
                    null
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                firstColumnWords.forEachIndexed { index, word ->
                    OutlinedTextField(
                        word,
                        onValueChange = {},
                        modifier = Modifier.padding(8.dp),
                        readOnly = true,
                        prefix = {
                            Text("${index + 1} - ")
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                secondColumnWords.forEachIndexed { index, word ->
                    OutlinedTextField(
                        word,
                        onValueChange = {},
                        readOnly = true,
                        prefix = {
                            Text("${index + 1 + firstColumnWords.size} - ")
                        },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                modifier = Modifier
                    .height(50.dp),
                onClick = {
                    scope.launch {
                        @Suppress("KotlinConstantConditions")
                        if (BuildConfig.FLAVOR != "offline" && PackageUtils.isOrbotInstalled(context)) {
                            pageState.animateScrollToPage(1)
                        } else {
                            pageState.animateScrollToPage(2)
                        }
                    }
                },
            ) {
                Text(text = stringResource(R.string.next))
            }
        }
    }
}

@Composable
fun SignPolicyScreen(
    accountViewModel: AccountStateViewModel,
    key: String,
    password: String,
    useProxy: Boolean,
    proxyPort: Int,
    seedWords: Set<String>,
) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(0.dp))

        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmberLogo(size = 150)
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.sign_policy_title),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 28.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.sign_policy_explainer),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

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
                        option.explainer?.let {
                            Text(
                                text = it,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = {
                    if (key.isBlank()) {
                        accountViewModel.newKey(
                            useProxy = useProxy,
                            proxyPort = proxyPort,
                            signPolicy = selectedOption,
                            seedWords = seedWords,
                        )
                    } else {
                        accountViewModel.startUI(
                            key,
                            password,
                            null,
                            useProxy = useProxy,
                            proxyPort = proxyPort,
                            signPolicy = selectedOption,
                        )
                    }
                },
            ) {
                Text(text = stringResource(R.string.finish))
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun LoginPage(accountViewModel: AccountStateViewModel) {
    val pageState = rememberPagerState {
        2
    }

    val key = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    var dialogOpen by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val password = remember { mutableStateOf(TextFieldValue("")) }
    val needsPassword =
        remember {
            derivedStateOf {
                key.value.text.startsWith("ncryptsec1")
            }
        }
    val useProxy = remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf("9050") }
    var connectOrbotDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    HorizontalPager(
        state = pageState,
        modifier = Modifier.fillMaxSize(),
    ) { currentPage ->
        when (currentPage) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // The first child is glued to the top.
                    // Hence we have nothing at the top, an empty box is used.
                    Box(modifier = Modifier.height(0.dp))

                    // The second child, this column, is centered vertically.
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = "NostrSigner",
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(colorResource(id = R.color.amber)),
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        var showPassword by remember {
                            mutableStateOf(false)
                        }

                        var showCharsPassword by remember { mutableStateOf(false) }

                        val autofillNodeKey =
                            AutofillNode(
                                autofillTypes = listOf(AutofillType.Password),
                                onFill = { key.value = TextFieldValue(it) },
                            )

                        val autofillNodePassword =
                            AutofillNode(
                                autofillTypes = listOf(AutofillType.Password),
                                onFill = { key.value = TextFieldValue(it) },
                            )

                        val autofill = LocalAutofill.current
                        LocalAutofillTree.current += autofillNodeKey
                        LocalAutofillTree.current += autofillNodePassword

                        Text(
                            stringResource(R.string.add_your_key),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        OutlinedTextField(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    autofillNodeKey.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    autofill?.run {
                                        if (focusState.isFocused) {
                                            requestAutofillForNode(autofillNodeKey)
                                        } else {
                                            cancelAutofillForNode(autofillNodeKey)
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            value = key.value,
                            onValueChange = { key.value = it },
                            keyboardOptions = KeyboardOptions(
                                autoCorrect = false,
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
                                    SimpleQrCodeScanner {
                                        dialogOpen = false
                                        if (!it.isNullOrEmpty()) {
                                            key.value = TextFieldValue(it)
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
                                        if (accountViewModel.isValidKey(key.value.text, password.value.text)) {
                                            scope.launch {
                                                pageState.animateScrollToPage(1)
                                            }
                                        } else {
                                            errorMessage = context.getString(R.string.invalid_key)
                                        }
                                    }
                                },
                            ),
                        )
                        if (errorMessage.isNotBlank()) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (needsPassword.value) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        autofillNodePassword.boundingBox = coordinates.boundsInWindow()
                                    }
                                    .onFocusChanged { focusState ->
                                        autofill?.run {
                                            if (focusState.isFocused) {
                                                requestAutofillForNode(autofillNodePassword)
                                            } else {
                                                cancelAutofillForNode(autofillNodePassword)
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(18.dp),
                                value = password.value,
                                onValueChange = {
                                    password.value = it
                                    if (errorMessage.isNotEmpty()) {
                                        errorMessage = ""
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
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
                                            if (accountViewModel.isValidKey(key.value.text, password.value.text)) {
                                                scope.launch {
                                                    pageState.animateScrollToPage(1)
                                                }
                                            } else {
                                                errorMessage = context.getString(R.string.invalid_key)
                                            }
                                        }
                                    },
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        @Suppress("KotlinConstantConditions")
                        if (BuildConfig.FLAVOR != "offline" && PackageUtils.isOrbotInstalled(context)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = useProxy.value,
                                    onCheckedChange = {
                                        if (it) {
                                            connectOrbotDialogOpen = true
                                        } else {
                                            useProxy.value = false
                                        }
                                    },
                                )

                                Text(stringResource(R.string.connect_through_your_orbot_setup))
                            }

                            if (connectOrbotDialogOpen) {
                                ConnectOrbotDialog(
                                    onClose = { connectOrbotDialogOpen = false },
                                    onPost = {
                                        connectOrbotDialogOpen = false
                                        useProxy.value = true
                                    },
                                    onError = {
                                        scope.launch {
                                            Toast.makeText(
                                                context,
                                                it,
                                                Toast.LENGTH_LONG,
                                            )
                                                .show()
                                        }
                                    },
                                    proxyPort,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (key.value.text.isBlank()) {
                                        errorMessage = context.getString(R.string.key_is_required)
                                    }

                                    if (needsPassword.value && password.value.text.isBlank()) {
                                        errorMessage = context.getString(R.string.password_is_required)
                                    }

                                    if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                                        if (accountViewModel.isValidKey(key.value.text, password.value.text)) {
                                            scope.launch {
                                                pageState.animateScrollToPage(1)
                                            }
                                        } else {
                                            errorMessage = context.getString(R.string.invalid_key)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .height(50.dp),
                            ) {
                                Text(text = stringResource(R.string.next))
                            }
                        }
                    }
                }
            }
            else -> {
                SignPolicyScreen(
                    accountViewModel = accountViewModel,
                    key = key.value.text,
                    password = password.value.text,
                    useProxy = useProxy.value,
                    proxyPort = proxyPort.value.toInt(),
                    seedWords = emptySet(),
                )
            }
        }
    }
}
