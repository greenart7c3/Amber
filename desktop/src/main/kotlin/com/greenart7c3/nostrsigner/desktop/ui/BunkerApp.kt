package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.AccountStore
import com.greenart7c3.nostrsigner.desktop.data.BunkerDatabase
import com.greenart7c3.nostrsigner.desktop.data.ConnectedApp
import com.greenart7c3.nostrsigner.desktop.data.SqliteBunkerHistoryLogger
import com.greenart7c3.nostrsigner.desktop.data.SqliteBunkerPermissionStore
import com.greenart7c3.nostrsigner.desktop.relay.BunkerRelayConnection
import com.greenart7c3.nostrsigner.shared.BunkerSigner
import com.greenart7c3.nostrsigner.shared.BunkerSigningEngine
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BunkerApp(scope: CoroutineScope) {
    var account by remember { mutableStateOf<KeyPair?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        account = AccountStore.load()
        loading = false
    }

    MaterialTheme {
        Surface {
            when {
                loading -> Text("Loading...", modifier = Modifier.padding(24.dp))
                account == null -> SetupScreen(
                    onGenerate = { scope.launch { account = AccountStore.generate() } },
                    onImport = { hex -> scope.launch { account = AccountStore.import(hex) } },
                )
                else -> ConnectedMainScreen(account!!, scope)
            }
        }
    }
}

@Composable
private fun SetupScreen(onGenerate: () -> Unit, onImport: (String) -> Unit) {
    var importText by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Set up your Amber Bunker account", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGenerate) { Text("Generate a new key") }
        Spacer(Modifier.height(24.dp))
        Text("Or import an existing private key (hex)")
        OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onImport(importText.trim()) }, enabled = importText.isNotBlank()) { Text("Import") }
    }
}

@Composable
private fun ConnectedMainScreen(account: KeyPair, scope: CoroutineScope) {
    val pubKeyHex = account.pubKey.toHexKey()

    val connection = remember(pubKeyHex) {
        val db = BunkerDatabase.open()
        val permissionStore = SqliteBunkerPermissionStore(db)
        val historyLogger = SqliteBunkerHistoryLogger(db)
        val approvalPort = DesktopApprovalPort()
        val engine = BunkerSigningEngine(BunkerSigner(account), permissionStore, approvalPort, historyLogger)
        val relayConnection = BunkerRelayConnection(pubKeyHex, engine, scope)
        relayConnection.start()
        Triple(relayConnection, approvalPort, historyLogger)
    }
    val (relayConnection, approvalPort, historyLogger) = connection

    val bunkerUri = remember(pubKeyHex) {
        "bunker://$pubKeyHex?" + relayConnection.relays.joinToString("&") { "relay=${it.url}" }
    }
    val qrImage = remember(bunkerUri) { qrCodeImageBitmap(bunkerUri) }
    val pending by approvalPort.pending.collectAsState()
    var connectedApps by remember { mutableStateOf<List<ConnectedApp>>(emptyList()) }

    LaunchedEffect(Unit) { connectedApps = historyLogger.connectedApps() }

    pending.firstOrNull()?.let { entry ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Signing request") },
            text = {
                Column {
                    Text("App: ${entry.request.appPubKey.take(12)}...")
                    Text("Method: ${entry.request.method}")
                    entry.request.kind?.let { Text("Kind: $it") }
                }
            },
            confirmButton = {
                TextButton(onClick = { approvalPort.answer(entry, approved = true, remember = true) }) { Text("Always allow") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { approvalPort.answer(entry, approved = true, remember = false) }) { Text("Allow once") }
                    TextButton(onClick = { approvalPort.answer(entry, approved = false, remember = false) }) { Text("Reject") }
                }
            },
        )
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Amber Bunker", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Public key: $pubKeyHex")
        Spacer(Modifier.height(16.dp))
        Image(qrImage, contentDescription = "Bunker connection QR code")
        Spacer(Modifier.height(8.dp))
        Text(bunkerUri, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Text("Connected apps", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(connectedApps) { app ->
                Text(app.name.ifBlank { app.pubKey.take(16) + "..." })
            }
        }
    }
}
