package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import kotlinx.coroutines.launch

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
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Amber",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Nostr event signer",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Add a key") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Create a new key") })
                }
                Spacer(Modifier.height(24.dp))

                if (tab == 0) {
                    ImportKeyPane(scope)
                } else {
                    NewKeyPane(scope)
                }

                if (hasAccounts) {
                    TextButton(onClick = { Session.addingAccount.value = false }) {
                        Text("Cancel")
                    }
                }
            }
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
        label = { Text("nsec, ncryptsec, hex key or seed words") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    if (key.trim().startsWith("ncryptsec")) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
    Spacer(Modifier.height(16.dp))
    AmberButton(
        text = if (working) "Adding…" else "Add key",
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
                        Toaster.toast(it.message ?: "Invalid key")
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

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Text("Your seed words", style = MaterialTheme.typography.titleMedium)
    Text(
        "Write them down and keep them safe. They are the only backup of your new key.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            seedWords.chunked(4).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { index, word ->
                        Text(
                            "${rowIndex * 4 + index + 1}. $word",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    AmberButton(
        text = if (working) "Creating…" else "Create account",
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
                        Toaster.toast(it.message ?: "Failed to create the key")
                    },
                )
                working = false
            }
        },
    )
}
