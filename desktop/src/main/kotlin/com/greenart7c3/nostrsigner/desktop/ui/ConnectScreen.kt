package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.ui.qrCodeImageBitmap

@Composable
fun ConnectScreen(bunkerUri: String?, onCopyUri: (String) -> Unit) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Connect a client", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Scan this QR code, or paste the connection string, in a NIP-46-compatible Nostr client.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        if (bunkerUri == null) {
            Text("Connecting to relays…")
            return@Column
        }

        val qrImage = remember(bunkerUri) { qrCodeImageBitmap(bunkerUri) }
        Image(qrImage, contentDescription = "Bunker connection QR code")
        Spacer(Modifier.height(16.dp))
        SelectionContainer {
            Text(bunkerUri, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onCopyUri(bunkerUri) }) { Text("Copy to clipboard") }
    }
}
