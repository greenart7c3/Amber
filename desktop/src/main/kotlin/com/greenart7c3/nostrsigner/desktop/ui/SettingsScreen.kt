package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    relays: List<String>,
    onAddRelay: (String) -> Unit,
    onRemoveRelay: (String) -> Unit,
    relayValidator: (String) -> Boolean,
    dataDirPath: String,
    onRevealDataDir: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(24.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.padding(top = 8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeButton(mode, selected = mode == themeMode, onClick = { onThemeModeChange(mode) })
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Relays", style = MaterialTheme.typography.titleMedium)
        Text(
            "Changes apply the next time Amber Bunker starts.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        relays.forEach { relay ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(relay, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveRelay(relay) }) { Text("✕") }
            }
        }

        var newRelay by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newRelay,
                onValueChange = {
                    newRelay = it
                    error = false
                },
                placeholder = { Text("wss://relay.example.com") },
                isError = error,
                modifier = Modifier.weight(1f),
            )
            Button(
                modifier = Modifier.padding(start = 8.dp),
                onClick = {
                    if (relayValidator(newRelay)) {
                        onAddRelay(newRelay.trim())
                        newRelay = ""
                    } else {
                        error = true
                    }
                },
            ) { Text("Add") }
        }

        Spacer(Modifier.height(24.dp))
        Text("Data directory", style = MaterialTheme.typography.titleMedium)
        Text(dataDirPath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Button(modifier = Modifier.padding(top = 8.dp), onClick = onRevealDataDir) { Text("Reveal in file manager") }
    }
}

@Composable
private fun ThemeModeButton(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val label = when (mode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    Button(
        modifier = Modifier.padding(end = 8.dp),
        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) { Text(label) }
}
