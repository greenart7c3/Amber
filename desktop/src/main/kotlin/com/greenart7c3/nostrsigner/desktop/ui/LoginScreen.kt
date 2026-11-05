package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.d_add_a_key
import com.greenart7c3.nostrsigner.commonsui.d_add_key
import com.greenart7c3.nostrsigner.commonsui.d_adding
import com.greenart7c3.nostrsigner.commonsui.d_copy_seed_words_warning
import com.greenart7c3.nostrsigner.commonsui.d_create_a_new_key
import com.greenart7c3.nostrsigner.commonsui.d_create_account
import com.greenart7c3.nostrsigner.commonsui.d_creating
import com.greenart7c3.nostrsigner.commonsui.d_failed_create_key
import com.greenart7c3.nostrsigner.commonsui.d_hide_seed_words
import com.greenart7c3.nostrsigner.commonsui.d_import_key_label
import com.greenart7c3.nostrsigner.commonsui.d_invalid_key
import com.greenart7c3.nostrsigner.commonsui.d_name_optional
import com.greenart7c3.nostrsigner.commonsui.d_password
import com.greenart7c3.nostrsigner.commonsui.d_seed_words_copied
import com.greenart7c3.nostrsigner.commonsui.d_seed_words_desc
import com.greenart7c3.nostrsigner.commonsui.d_tagline
import com.greenart7c3.nostrsigner.commonsui.d_your_seed_words
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.show_seed_words
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    hasAccounts: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        Toaster.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val scrollState = rememberScrollState()
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).padding(24.dp).verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    BuildVariant.appName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.d_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                AmberTabRow(
                    selectedTabIndex = tab,
                    titles = listOf(stringResource(Res.string.d_add_a_key), stringResource(Res.string.d_create_a_new_key)),
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(24.dp))

                if (tab == 0) {
                    ImportKeyPane(scope)
                } else {
                    NewKeyPane(scope)
                }

                if (hasAccounts) {
                    AmberTextButton(
                        text = stringResource(Res.string.cancel),
                        onClick = { Session.addingAccount.value = false },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ImportKeyPane(scope: kotlinx.coroutines.CoroutineScope) {
    var key by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(Res.string.d_import_key_label)) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    if (key.trim().startsWith("ncryptsec")) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.d_password)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
    Spacer(Modifier.height(16.dp))
    AmberButton(
        text = if (working) stringResource(Res.string.d_adding) else stringResource(Res.string.d_add_key),
        fillWidth = true,
        enabled = key.isNotBlank() && !working,
        onClick = {
            working = true
            scope.launch {
                AccountManager.parseKey(key, password).fold(
                    onSuccess = { keyPair ->
                        val account = AccountManager.addAccount(
                            keyPair = keyPair,
                            seedWords = if (key.trim().contains(" ")) key.trim() else "",
                            didBackup = true,
                        )
                        Session.onAccountAdded(account)
                    },
                    onFailure = {
                        Toaster.toast(it.message ?: getStringBlocking(Res.string.d_invalid_key))
                    },
                )
                working = false
            }
        },
    )
}

@Composable
private fun NewKeyPane(scope: kotlinx.coroutines.CoroutineScope) {
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val seedWords = remember { AccountManager.generateSeedWords() }
    var revealed by remember { mutableStateOf(false) }
    var confirmCopy by remember { mutableStateOf(false) }
    val copyText = rememberCopyText()

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(Res.string.d_name_optional)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.d_your_seed_words), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(Res.string.d_seed_words_desc),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        if (!revealed) {
            // Hidden until asked for, so they don't sit on screen (or in a screen share) by default.
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                AmberOutlinedButton(text = stringResource(Res.string.show_seed_words), onClick = { revealed = true })
            }
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val columns = if (LocalLayoutWidth.current == LayoutWidth.COMPACT) 3 else 4
                seedWords.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { index, word ->
                            Text(
                                "${rowIndex * columns + index + 1}. $word",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AmberTextButton(text = stringResource(Res.string.d_hide_seed_words), onClick = { revealed = false })
                    AmberTextButton(text = stringResource(Res.string.copy_to_clipboard), onClick = { confirmCopy = true })
                }
            }
        }
    }
    if (confirmCopy) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text(stringResource(Res.string.warning)) },
            text = { Text(stringResource(Res.string.d_copy_seed_words_warning)) },
            confirmButton = {
                AmberTextButton(
                    text = stringResource(Res.string.copy_to_clipboard),
                    onClick = {
                        confirmCopy = false
                        copyText(seedWords.joinToString(" "))
                        Toaster.toast(getStringBlocking(Res.string.d_seed_words_copied))
                    },
                )
            },
            dismissButton = {
                AmberTextButton(text = stringResource(Res.string.cancel), onClick = { confirmCopy = false })
            },
        )
    }
    Spacer(Modifier.height(16.dp))
    AmberButton(
        text = if (working) stringResource(Res.string.d_creating) else stringResource(Res.string.d_create_account),
        fillWidth = true,
        enabled = !working,
        onClick = {
            working = true
            scope.launch {
                AccountManager.parseKey(seedWords.joinToString(" ")).fold(
                    onSuccess = { keyPair ->
                        val account = AccountManager.addAccount(
                            keyPair = keyPair,
                            name = name,
                            seedWords = seedWords.joinToString(" "),
                            didBackup = false,
                        )
                        Session.onAccountAdded(account)
                    },
                    onFailure = {
                        Toaster.toast(it.message ?: getStringBlocking(Res.string.d_failed_create_key))
                    },
                )
                working = false
            }
        },
    )
}
