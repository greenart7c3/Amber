package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(onGenerate: () -> Unit, onImport: (String) -> Unit) {
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
